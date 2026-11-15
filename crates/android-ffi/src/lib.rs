use std::path::Path;

use craft_anything_engine::registry::Registry;
use craft_anything_engine::session::Session;
use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::jstring;
use serde_json::json;

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_craftanything_android_NativeEngine_nativeEngineSummary<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    data_dir: JString<'local>,
) -> jstring {
    let dir: String = env
        .get_string(&data_dir)
        .map(|s| s.into())
        .unwrap_or_else(|_| "/tmp".to_string());

    let registry = Registry::with_all_tools();
    let session = Session::isolated(Path::new(&dir));
    let toolset_count = registry.toolsets().len();

    let payload = json!({
        "engineVersion": env!("CARGO_PKG_VERSION"),
        "toolCount": registry.len(),
        "toolsetCount": toolset_count,
        "cleanRoom": session.clean_room(),
        "workspaceDir": dir,
    });

    env.new_string(payload.to_string())
        .expect("Failed to allocate JNI string")
        .into_raw()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_craftanything_android_NativeEngine_nativeListToolsJson<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jstring {
    let registry = Registry::with_all_tools();
    let tools: Vec<_> = registry
        .all()
        .iter()
        .map(|spec| {
            let schema = registry.input_schema(spec);
            json!({
                "id": spec.id,
                "title": spec.title,
                "summary": spec.summary,
                "beginner": spec.beginner,
                "schema": schema,
            })
        })
        .collect();

    let payload = json!({
        "count": tools.len(),
        "tools": tools,
    });

    env.new_string(payload.to_string())
        .expect("Failed to allocate JNI string")
        .into_raw()
}
