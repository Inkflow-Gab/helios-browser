//! JNI surface over [`adblock`], the filter engine that powers Brave's native adblocker.
//!
//! # Why native at all
//! Helios needs full Adblock Plus syntax: `$third-party`, `$domain=`, `@@` exceptions, generic and
//! site-specific cosmetic rules, scriptlets. Re-implementing that in Kotlin means re-implementing a
//! decade of accumulated edge cases, and the failure mode of getting it subtly wrong is ads leaking
//! through with no visible symptom. `adblock-rust` is the engine Brave ships, so the rules behave
//! the way the lists were written to behave.
//!
//! The cost is a Rust toolchain and the Android NDK in CI. That is paid in one place — the
//! `cargo ndk` step in `.github/workflows/build.yml` — and the resulting
//! `app/src/jniLibs/<abi>/libhelios_adblock.so` is picked up by AGP with no Gradle native
//! configuration at all.
//!
//! # Failure policy
//! Every entry point is wrapped in [`catch_unwind`] and **fails open**: a parse failure, a panic or
//! a missing engine reports "do not block" rather than throwing. A browser that shows an ad is
//! annoying; a browser that dies on startup is unusable.
//!
//! # Threading
//! [`Engine`] is `Sync` (the `single-thread` feature is off), and `check_network_request` takes
//! `&self`, so the common path only needs a read lock. The engine lives in a process-wide
//! [`RwLock`], matching the process-wide WebView data directory — there is no per-tab engine, and
//! blocking rules are not tab-specific anyway.
//!
//! [`catch_unwind`]: std::panic::catch_unwind

use adblock::lists::{FilterSet, ParseOptions};
use adblock::request::Request;
use adblock::Engine;
use jni::objects::{JByteArray, JClass, JString};
use jni::sys::{jboolean, jbyteArray, jstring, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;
use std::collections::HashSet;
use std::sync::{OnceLock, RwLock};

/// Process-wide engine. `None` until a list has been built successfully.
static ENGINE: OnceLock<RwLock<Option<Engine>>> = OnceLock::new();

fn engine_slot() -> &'static RwLock<Option<Engine>> {
    ENGINE.get_or_init(|| RwLock::new(None))
}

/// Reads the engine, if one is loaded and the lock is not poisoned.
///
/// A poisoned lock means some other thread panicked while holding the read guard. Treat it as
/// "no engine" rather than propagating the panic.
fn with_engine<R>(f: impl FnOnce(&Engine) -> R) -> Option<R> {
    let guard = engine_slot().read().ok()?;
    let engine = guard.as_ref()?;
    Some(f(engine))
}

/// Replaces the loaded engine, replacing rather than mutating in place so a concurrent reader sees
/// either the old engine or the new one, never a half-built one.
fn install(engine: Option<Engine>) {
    if let Ok(mut guard) = engine_slot().write() {
        *guard = engine;
    }
}

/// Runs `body`, converting a panic into `fallback`.
///
/// `AssertUnwindSafe` is the right call here: nothing observable is left inconsistent, because the
/// engine is only ever swapped in as a finished value.
///
/// # Note on `return` inside the block
/// `return X` inside `$body` returns from the *closure*, not from the JNI function. That is exactly
/// what is wanted: the closure's value becomes `Ok(X)`, which is what the function returns. So an
/// early `return JNI_FALSE` and a fall-through to `JNI_FALSE` are the same thing, and the two are
/// deliberately written to match. Do not "simplify" this into a `?` — the closure has no `Result`
/// to propagate through, and adding one would mean threading a fake error type for no gain.
macro_rules! fail_open {
    ($fallback:expr, $body:block) => {
        match std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| $body)) {
            Ok(value) => value,
            Err(_) => $fallback,
        }
    };
}

/// Converts a `jstring` to an owned `String`, or `None` if it is null or not valid UTF-8.
fn read_string(env: &mut JNIEnv, value: &JString) -> Option<String> {
    env.get_string(value).ok().map(|s| s.into())
}

/// Converts an owned `String` to a `jstring`, or null on failure.
fn write_string(env: &mut JNIEnv, value: String) -> jstring {
    env.new_string(value)
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

/// Selectors come back as one newline-separated string rather than a `String[]`.
///
/// A jstringArray would mean a second JNI signature and an array of `JString` handles to unref on
/// the Kotlin side; newline-joined text is one copy, and CSS selectors cannot contain a newline.
fn join_selectors(mut selectors: Vec<String>) -> String {
    selectors.retain(|s| !s.trim().is_empty());
    selectors.join("\n")
}

/// Splits a comma-separated class/id list gathered from the page.
fn split_list(value: &str) -> Vec<String> {
    value
        .split(',')
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .map(str::to_owned)
        .collect()
}

// ---------------------------------------------------------------------------------------------
// Lifecycle
// ---------------------------------------------------------------------------------------------

/// Always true. Its real job is to be the symbol whose presence proves `System.loadLibrary`
/// succeeded, so the Kotlin side never calls into a half-initialised library.
#[no_mangle]
pub extern "system" fn Java_com_helios_browser_engine_NativeAdBlock_nativeAvailable(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    JNI_TRUE
}

/// Parses the given filter-list text and installs a fresh engine, replacing any previous one.
///
/// This is the slow path: it is several seconds of work over a few megabytes of rules, which is why
/// [`nativeDeserialize`] exists and the Kotlin side tries it first.
///
/// Takes one string rather than one per list. Every list Helios ships is Adblock Plus syntax, and
/// `FilterSet::add_filter_list` concatenates its sources anyway, so joining them with a newline on
/// the Kotlin side is semantically identical and saves a second multi-megabyte JNI string. A list
/// header (`! Title:`) inside the text is still parsed, so metadata survives.
///
/// Returns true if the engine is now loaded. Empty text yields an engine with no rules, which is
/// valid and simply blocks nothing.
#[no_mangle]
pub extern "system" fn Java_com_helios_browser_engine_NativeAdBlock_nativeLoad(
    mut env: JNIEnv,
    _class: JClass,
    list_text: JString,
) -> jboolean {
    fail_open!(JNI_FALSE, {
        let Some(list_text) = read_string(&mut env, &list_text) else {
            return JNI_FALSE;
        };
        if list_text.is_empty() {
            // An engine with no lists would report "ready" while blocking nothing, which is a lie
            // the shields sheet would then repeat. Refuse instead.
            return JNI_FALSE;
        }

        // `debug = false`: keeps the original rule text out of memory. Helios does not surface
        // "which rule blocked this", so paying for it would be pure overhead.
        let mut filter_set = FilterSet::new(false);
        filter_set.add_filter_list(list_text, ParseOptions::default());

        let engine = Engine::new_with_filter_set(filter_set);
        install(Some(engine));
        JNI_TRUE
    })
}

/// Serialises the engine for caching to disk.
///
/// Returns null if no engine is loaded. The caller writes the bytes to a file and, on the next cold
/// start, hands them back to [`nativeDeserialize`].
#[no_mangle]
pub extern "system" fn Java_com_helios_browser_engine_NativeAdBlock_nativeSerialize(
    env: JNIEnv,
    _class: JClass,
) -> jbyteArray {
    fail_open!(std::ptr::null_mut(), {
        let Some(bytes) = with_engine(|engine| engine.serialize()) else {
            return std::ptr::null_mut();
        };
        match env.byte_array_from_slice(&bytes) {
            // `into_raw` yields a `jarray`, which is a distinct raw type from `jbyteArray` even
            // though both are pointers. The cast is a no-op at runtime; it only satisfies the type
            // checker, and JNI treats them interchangeably.
            Ok(array) => array.into_raw() as jbyteArray,
            Err(_) => std::ptr::null_mut(),
        }
    })
}

/// Restores an engine previously produced by [`nativeSerialize`].
///
/// Returns false on a checksum or version mismatch, in which case the caller must fall back to
/// [`nativeLoad`]. The stored engine is only swapped in once deserialisation has fully succeeded.
#[no_mangle]
pub extern "system" fn Java_com_helios_browser_engine_NativeAdBlock_nativeDeserialize(
    env: JNIEnv,
    _class: JClass,
    data: JByteArray,
) -> jboolean {
    fail_open!(JNI_FALSE, {
        let Ok(bytes) = env.convert_byte_array(&data) else {
            return JNI_FALSE;
        };
        // Build a throwaway engine first: `deserialize` mutates in place, and a partially
        // deserialised engine must never become the installed one.
        let mut engine = Engine::default();
        if engine.deserialize(&bytes).is_err() {
            return JNI_FALSE;
        }
        install(Some(engine));
        JNI_TRUE
    })
}

/// Drops the engine, freeing its memory. Used when blocking is switched off in settings.
#[no_mangle]
pub extern "system" fn Java_com_helios_browser_engine_NativeAdBlock_nativeRelease(
    _env: JNIEnv,
    _class: JClass,
) {
    install(None);
}

// ---------------------------------------------------------------------------------------------
// Network blocking
// ---------------------------------------------------------------------------------------------

/// Decides whether a single request should be blocked.
///
/// @param url the full request URL.
/// @param source_url the top-level document making the request. This is what makes `$third-party`
///   and `$domain=` rules behave correctly; pass an empty string only when it is genuinely unknown.
/// @param request_type an adblock-rust content type token (`main_frame`, `script`, `image`, …).
///   Android does not expose one, so the Kotlin side infers it — see `WebRequestClassifier`.
/// @param method the HTTP method; empty or unrecognised is treated as a GET.
#[no_mangle]
pub extern "system" fn Java_com_helios_browser_engine_NativeAdBlock_nativeCheck(
    mut env: JNIEnv,
    _class: JClass,
    url: JString,
    source_url: JString,
    request_type: JString,
    method: JString,
) -> jboolean {
    fail_open!(JNI_FALSE, {
        // Sequential rather than a tuple: `get_string` takes `&mut JNIEnv`, so four calls in one
        // expression would be four overlapping mutable borrows and would not compile.
        let Some(url) = read_string(&mut env, &url) else {
            return JNI_FALSE;
        };
        let Some(source_url) = read_string(&mut env, &source_url) else {
            return JNI_FALSE;
        };
        let Some(request_type) = read_string(&mut env, &request_type) else {
            return JNI_FALSE;
        };
        let Some(method) = read_string(&mut env, &method) else {
            return JNI_FALSE;
        };

        // The closure must return `jboolean` throughout: the `return JNI_FALSE` statements below fix
        // the closure's return type as `u8`, so a bare `bool` from `should_block()` would not
        // unify with it. Hence the explicit `as jboolean` on the tail expression.
        with_engine(|engine| {
            // A URL adblock-rust cannot parse is one this browser cannot load either, so failing
            // open costs nothing.
            let Ok(request) = Request::new(&url, &source_url, &request_type, &method) else {
                return JNI_FALSE;
            };
            if !request.is_supported {
                return JNI_FALSE;
            }
            engine.check_network_request(&request).should_block() as jboolean
        })
        .unwrap_or(JNI_FALSE)
    })
}

// ---------------------------------------------------------------------------------------------
// Cosmetic filtering
// ---------------------------------------------------------------------------------------------

/// Site-specific CSS selectors to hide for [url], newline separated.
///
/// Returns an empty string when the page has no cosmetic rules, which is the common case.
#[no_mangle]
pub extern "system" fn Java_com_helios_browser_engine_NativeAdBlock_nativeHideSelectors(
    mut env: JNIEnv,
    _class: JClass,
    url: JString,
) -> jstring {
    fail_open!(std::ptr::null_mut(), {
        let Some(url) = read_string(&mut env, &url) else {
            return std::ptr::null_mut();
        };
        let selectors = with_engine(|engine| join_selectors(
            engine.url_cosmetic_resources(&url).hide_selectors.into_iter().collect(),
        ))
        .unwrap_or_default();
        write_string(&mut env, selectors)
    })
}

/// True when the page has opted out of generic cosmetic hiding with `$generichide`.
///
/// When this is true the page has said "do not guess about my classes and ids", so the generic-hide
/// round trip must be skipped entirely.
#[no_mangle]
pub extern "system" fn Java_com_helios_browser_engine_NativeAdBlock_nativeGenericHideDisabled(
    mut env: JNIEnv,
    _class: JClass,
    url: JString,
) -> jboolean {
    fail_open!(JNI_TRUE, {
        let Some(url) = read_string(&mut env, &url) else {
            return JNI_TRUE;
        };
        // A null string from Kotlin is read as "unknown page", and unknown must mean disabled: the
        // generic-hide round trip costs a JS evaluation on every navigation.
        with_engine(|engine| engine.url_cosmetic_resources(&url).generichide).unwrap_or(true)
            as jboolean
    })
}

/// CSS selectors from generic hiding rules that apply to the class and id names present on the page.
///
/// @param classes_csv comma-separated class names gathered from the live DOM.
/// @param ids_csv comma-separated element ids gathered from the live DOM.
/// @param url the page URL, needed to look up the matching `$generichide` and `#@#` exceptions.
#[no_mangle]
pub extern "system" fn Java_com_helios_browser_engine_NativeAdBlock_nativeHiddenClassIdSelectors(
    mut env: JNIEnv,
    _class: JClass,
    url: JString,
    classes_csv: JString,
    ids_csv: JString,
) -> jstring {
    fail_open!(std::ptr::null_mut(), {
        // Sequential, not a tuple: `get_string` takes `&mut JNIEnv`, so these cannot overlap.
        let Some(url) = read_string(&mut env, &url) else {
            return std::ptr::null_mut();
        };
        let Some(classes_csv) = read_string(&mut env, &classes_csv) else {
            return std::ptr::null_mut();
        };
        let Some(ids_csv) = read_string(&mut env, &ids_csv) else {
            return std::ptr::null_mut();
        };

        let classes = split_list(&classes_csv);
        let ids = split_list(&ids_csv);
        if classes.is_empty() && ids.is_empty() {
            return write_string(&mut env, String::new());
        }

        let selectors = with_engine(|engine| {
            let resources = engine.url_cosmetic_resources(&url);
            // Exceptions come from the same lookup as the hide selectors; they are the `#@#`
            // entries, and they must be passed back in or opt-outs get hidden anyway.
            let exceptions: HashSet<String> = resources.exceptions;
            engine
                .hidden_class_id_selectors(classes, ids, &exceptions)
                .into_iter()
                .collect()
        })
        .unwrap_or_default();

        write_string(&mut env, join_selectors(selectors))
    })
}

/// Scriptlet JavaScript to inject into [url].
///
/// Covers uBlock Origin's `##+js(...)` procedural rules, which are how modern lists neuter
/// anti-adblock and pop-up interstitials. Empty when the page needs none.
#[no_mangle]
pub extern "system" fn Java_com_helios_browser_engine_NativeAdBlock_nativeInjectedScript(
    mut env: JNIEnv,
    _class: JClass,
    url: JString,
) -> jstring {
    fail_open!(std::ptr::null_mut(), {
        let Some(url) = read_string(&mut env, &url) else {
            return std::ptr::null_mut();
        };
        let script = with_engine(|engine| engine.url_cosmetic_resources(&url).injected_script)
            .unwrap_or_default();
        write_string(&mut env, script)
    })
}

// ---------------------------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;
    // Only the hosts-format test needs this; importing it at the top level would be an unused import
    // in every non-test build.
    use adblock::lists::FilterFormat;

    fn engine_from(list: &str) -> Engine {
        let mut filter_set = FilterSet::new(false);
        filter_set.add_filter_list(list.to_owned(), ParseOptions::default());
        Engine::new_with_filter_set(filter_set)
    }

    fn blocked(engine: &Engine, url: &str, source: &str, kind: &str) -> bool {
        let request = Request::new(url, source, kind, "get").expect("request should parse");
        engine.check_network_request(&request).should_block()
    }

    #[test]
    fn blocks_listed_host() {
        let engine = engine_from("||exampleads.test^");
        assert!(blocked(&engine, "https://exampleads.test/banner.gif", "https://site.test/", "image"));
    }

    #[test]
    fn allows_unlisted_host() {
        let engine = engine_from("||exampleads.test^");
        assert!(!blocked(&engine, "https://site.test/app.js", "https://site.test/", "script"));
    }

    /// The single most important test here: EasyPrivacy contains rules scoped with
    /// `$third-party` precisely so a site is not blocked from loading its own CDN. An engine that
    /// ignored the option would break first-party requests across the whole web.
    #[test]
    fn third_party_rule_leaves_first_party_alone() {
        let engine = engine_from("||tracker.test^$third-party");
        assert!(blocked(
            &engine,
            "https://tracker.test/pixel",
            "https://site.test/",
            "image"
        ));
        assert!(!blocked(
            &engine,
            "https://tracker.test/app.js",
            "https://tracker.test/",
            "script"
        ));
    }

    #[test]
    fn exception_rule_overrides_block() {
        let engine = engine_from("||tracker.test^\n@@||tracker.test/ok.js");
        assert!(blocked(&engine, "https://tracker.test/pixel", "https://site.test/", "image"));
        assert!(!blocked(&engine, "https://tracker.test/ok.js", "https://site.test/", "script"));
    }

    /// `$domain=` is an allowlist of documents, not a blocklist. The rule applies *only* when the
    /// top-level document is one of the listed domains — which is the opposite of the intuitive
    /// reading, and the reason the first assertion below is a `!`.
    ///
    /// Getting this backwards is not a hypothetical: ad networks use `$domain=` to scope an
    /// aggressive rule to the sites that actually serve the ads, so a browser that inverts it
    /// either misses every ad or breaks every other site on the web.
    #[test]
    fn domain_scoped_rule_applies_only_on_listed_documents() {
        let engine = engine_from("||widget.test^$domain=allowed.test");

        // On a listed document the rule is in force.
        assert!(blocked(
            &engine,
            "https://widget.test/x.js",
            "https://allowed.test/",
            "script"
        ));

        // On any other document the rule does not exist, so the request is left alone.
        assert!(!blocked(
            &engine,
            "https://widget.test/x.js",
            "https://other.test/",
            "script"
        ));
    }

    /// A listed domain covers its subdomains. Ad networks write `$domain=example.com` meaning
    /// "and every www./m./sub. under it", so a strict equality match would silently under-block.
    #[test]
    fn domain_scoped_rule_includes_subdomains() {
        let engine = engine_from("||widget.test^$domain=allowed.test");
        assert!(blocked(
            &engine,
            "https://widget.test/x.js",
            "https://www.allowed.test/",
            "script"
        ));
    }

    /// `~domain=` is the negation form: apply everywhere *except* the listed documents.
    #[test]
    fn negated_domain_rule_excludes_listed_documents() {
        let engine = engine_from("||widget.test^$domain=~safe.test");
        assert!(blocked(
            &engine,
            "https://widget.test/x.js",
            "https://other.test/",
            "script"
        ));
        assert!(!blocked(
            &engine,
            "https://widget.test/x.js",
            "https://safe.test/",
            "script"
        ));
    }

    #[test]
    fn cosmetic_selectors_are_returned_for_the_page() {
        let engine = engine_from("site.test##.ad-slot");
        let resources = engine.url_cosmetic_resources("https://site.test/page");
        assert!(resources.hide_selectors.contains(".ad-slot"));
        assert!(engine
            .url_cosmetic_resources("https://other.test/page")
            .hide_selectors
            .is_empty());
    }

    #[test]
    fn generic_hide_returns_rules_for_present_class() {
        let engine = engine_from("##.ad-wrapper");
        let resources = engine.url_cosmetic_resources("https://site.test/page");
        let selectors = engine.hidden_class_id_selectors(
            vec!["ad-wrapper"],
            Vec::<String>::new(),
            &resources.exceptions,
        );
        assert!(selectors.iter().any(|s| s.contains("ad-wrapper")));
    }

    #[test]
    fn unsupported_scheme_is_not_blocked() {
        let engine = engine_from("||tracker.test^");
        let request = Request::new("ftp://tracker.test/x", "https://site.test/", "other", "get")
            .expect("request should parse");
        assert!(!request.is_supported);
        assert!(!engine.check_network_request(&request).should_block());
    }

    #[test]
    fn serialise_round_trips() {
        let engine = engine_from("||tracker.test^\nsite.test##.ad");
        let bytes = engine.serialize();
        assert!(!bytes.is_empty());

        let mut restored = Engine::default();
        restored.deserialize(&bytes).expect("round trip should deserialize");
        assert!(blocked(&restored, "https://tracker.test/p", "https://site.test/", "image"));
        assert!(restored
            .url_cosmetic_resources("https://site.test/")
            .hide_selectors
            .contains(".ad"));
    }

    #[test]
    fn corrupt_serialised_bytes_are_rejected() {
        let mut engine = Engine::default();
        assert!(engine.deserialize(b"not an engine").is_err());
    }

    /// `ParseOptions::format` must actually be honoured, otherwise a hosts file would be parsed as
    /// ABP text and silently match nothing.
    #[test]
    fn hosts_format_is_understood() {
        let mut filter_set = FilterSet::new(false);
        filter_set.add_filter_list(
            "0.0.0.0 hosts-tracker.test\n127.0.0.1 other-tracker.test\n".to_owned(),
            ParseOptions {
                format: FilterFormat::Hosts,
                ..ParseOptions::default()
            },
        );
        let engine = Engine::new_with_filter_set(filter_set);
        assert!(blocked(&engine, "https://hosts-tracker.test/a", "https://site.test/", "image"));
        assert!(blocked(&engine, "https://other-tracker.test/a", "https://site.test/", "image"));
    }

    #[test]
    fn split_list_trims_and_drops_blanks() {
        assert_eq!(split_list(" a , ,b ,"), vec!["a", "b"]);
        assert!(split_list("").is_empty());
    }
}
