#!/usr/bin/env bash
set -e

log() {
  echo "[android-setup] $*" >&2
}

fail() {
  log "ERROR: $*"
  exit 1
}

command_exists() {
  command -v "$1" >/dev/null 2>&1
}

speed_to_int() {
  local speed="$1"
  if [[ ! "$speed" =~ ^[0-9]+(\.[0-9]+)?$ ]]; then
    echo 0
    return
  fi
  local speed_int="${speed%.*}"
  if [[ -z "$speed_int" ]]; then
    speed_int=0
  fi
  echo "$speed_int"
}

GRADLE_VERSION="9.1.0"
GRADLE_ROOT="${GRADLE_ROOT:-$HOME/gradle}"
GRADLE_DIST="gradle-${GRADLE_VERSION}"
GRADLE_ZIP="${GRADLE_ROOT}/${GRADLE_DIST}-bin.zip"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
SCRIPT_DIR=""
export GRADLE_USER_HOME
APT_UPDATED=0

ping_host() {
  local host="$1"
  local ping_cmd=""
  if command_exists ping; then
    ping_cmd="ping"
  elif command_exists busybox; then
    ping_cmd="busybox ping"
  fi
  if [[ -z "$ping_cmd" ]]; then
    log "ping not found; skipping mirror checks"
    return 2
  fi
  if $ping_cmd -c 1 -W 2 "$host" >/dev/null 2>&1; then
    log "Ping OK: $host"
    return 0
  fi
  log "Ping fail: $host"
  return 1
}

select_download_url() {
  local label="$1"
  local default_url="$2"
  local default_host="$3"
  shift 3
  local mirror_args=("$@")

  log "Selecting fastest mirror for $label"

  if command_exists curl; then
    local best_url="$default_url"
    local best_speed=0

    local probe_dir
    probe_dir=$(mktemp -d)

    {
      local speed
      speed=$(measure_download_speed "$default_url") || speed="0"
      printf '%s\t%s\n' "$(speed_to_int "$speed")" "$default_url" > "$probe_dir/default"
    } &

    local i=0
    local probe_idx=0
    while (( i < ${#mirror_args[@]} )); do
      local url="${mirror_args[$((i + 1))]}"
      local key="mirror_${probe_idx}"
      {
        local speed
        speed=$(measure_download_speed "$url") || speed="0"
        printf '%s\t%s\n' "$(speed_to_int "$speed")" "$url" > "$probe_dir/$key"
      } &
      i=$((i + 2))
      probe_idx=$((probe_idx + 1))
    done

    wait

    local result_file
    while IFS= read -r -d '' result_file; do
      local speed_int
      speed_int=$(cut -f1 "$result_file")
      local url
      url=$(cut -f2- "$result_file")
      if [[ -n "$speed_int" && "$speed_int" -gt "$best_speed" ]]; then
        best_speed="$speed_int"
        best_url="$url"
      fi
    done < <(find "$probe_dir" -type f -print0)

    rm -rf "$probe_dir"

    if [[ "$best_speed" -gt 0 ]]; then
      log "Fastest mirror selected for $label: $best_url (speed=${best_speed}B/s)"
      echo "$best_url"
      return 0
    fi

    log "Speed test failed for all mirrors; fallback to ping selection"
  fi

  if ping_host "$default_host"; then
    echo "$default_url"
    return 0
  fi

  local i=0
  while (( i < ${#mirror_args[@]} )); do
    local host="${mirror_args[$i]}"
    local url="${mirror_args[$((i + 1))]}"
    i=$((i + 2))
    if ping_host "$host"; then
      log "Using mirror for $label: $host"
      echo "$url"
      return 0
    fi
  done

  log "No reachable mirror; fallback to default URL for $label"
  echo "$default_url"
}

measure_download_speed() {
  local url="$1"
  # Download a small range to /dev/null and use curl's measured speed.
  # Use short timeouts to keep selection fast.
  curl -L \
    --range 0-524287 \
    --output /dev/null \
    --silent \
    --show-error \
    --connect-timeout 3 \
    --max-time 8 \
    -w "%{speed_download}" \
    "$url" 2>/dev/null
}

download_file() {
  local url="$1"
  local dest="$2"
  local max_retries=3
  local retry_count=0
  
  while [[ $retry_count -lt $max_retries ]]; do
    if command_exists curl; then
      if curl -L --connect-timeout 30 --max-time 120 --retry 2 --retry-delay 3 "$url" -o "$dest"; then
        return 0
      fi
    elif command_exists wget; then
      if wget --timeout=30 --tries=3 --waitretry=3 -O "$dest" "$url"; then
        return 0
      fi
    else
      log "curl or wget is required to download files."
      exit 1
    fi
    
    retry_count=$((retry_count + 1))
    log "Download failed, retrying ($retry_count/$max_retries)..."
    sleep 2
  done
  
  log "Failed to download file after $max_retries attempts: $url"
  return 1
}

install_packages() {
  local packages=("$@")
  if command_exists apt-get; then
    local sudo_cmd=""
    if command_exists sudo; then
      sudo_cmd="sudo"
    fi
    log "Installing packages: ${packages[*]}"
    if [[ "$APT_UPDATED" -eq 0 ]]; then
      $sudo_cmd apt-get update
      APT_UPDATED=1
    fi
    $sudo_cmd apt-get install -y "${packages[@]}"
  else
    log "apt-get not found; please install: ${packages[*]}"
  fi
}

ensure_ping() {
  if command_exists ping || command_exists busybox; then
    return
  fi
  if command_exists apt-get; then
    install_packages iputils-ping
  fi
  if ! command_exists ping && ! command_exists busybox; then
    log "ping still unavailable; mirror selection will be skipped"
  fi
}

ensure_java() {
  if command_exists java; then
    local version
    version=$(java -version 2>&1 | sed -n 's/.*version "\(.*\)".*/\1/p')
    local major=${version%%.*}
    if [[ "$major" == "1" ]]; then
      major=$(echo "$version" | cut -d. -f2)
    fi
    if [[ -n "$major" && "$major" -ge 17 ]]; then
      log "Java $version detected"
      return
    fi
    log "Java version $version is below 17; upgrading"
  else
    log "Java not found; installing OpenJDK 17"
  fi
  install_packages openjdk-17-jdk
}

resolve_java_home() {
  if [[ -n "${JAVA_HOME:-}" && -d "$JAVA_HOME" ]]; then
    return
  fi
  if command_exists java; then
    local java_path
    java_path=$(readlink -f "$(command -v java)")
    JAVA_HOME=$(dirname "$(dirname "$java_path")")
    export JAVA_HOME
  fi
}

ensure_android_tools() {
  ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android}}"
  export ANDROID_HOME
  export ANDROID_SDK_ROOT="$ANDROID_HOME"

  if [[ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]]; then
    log "Downloading Android command line tools"
    install_packages unzip
    mkdir -p "$ANDROID_HOME/cmdline-tools"
    local tmp_dir
    tmp_dir=$(mktemp -d)
    local zip_path="$tmp_dir/cmdline-tools.zip"
    local cmdline_url
    cmdline_url=$(select_download_url \
      "Android command line tools" \
      "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" \
      "dl.google.com" \
      "mirrors.tuna.tsinghua.edu.cn" "https://mirrors.tuna.tsinghua.edu.cn/android/repository/commandlinetools-linux-11076708_latest.zip" \
      "mirrors.bfsu.edu.cn" "https://mirrors.bfsu.edu.cn/android/repository/commandlinetools-linux-11076708_latest.zip" \
      "mirrors.aliyun.com" "https://mirrors.aliyun.com/android/repository/commandlinetools-linux-11076708_latest.zip")
    download_file "$cmdline_url" "$zip_path"
    unzip -q "$zip_path" -d "$ANDROID_HOME/cmdline-tools"
    mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
    rm -rf "$tmp_dir"
  fi

  export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
  log "Installing Android SDK packages"
  yes | sdkmanager --licenses >/dev/null || true
  sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
}

ensure_gradle() {
  install_packages unzip
  mkdir -p "$GRADLE_ROOT"
  if command_exists gradle; then
    local installed_version
    installed_version=$(gradle --version 2>/dev/null | grep -oP 'Gradle \K[0-9.]+' | head -1 || true)
    if [[ -n "$installed_version" ]]; then
      log "System Gradle detected: $installed_version (still preparing local Gradle zip for wrapper cache)"
    else
      log "System Gradle detected (version parse failed); preparing local Gradle zip for wrapper cache"
    fi
  fi

  if [[ ! -f "$GRADLE_ZIP" ]]; then
    log "Downloading Gradle ${GRADLE_VERSION}"
    local gradle_url
    gradle_url=$(select_download_url \
      "Gradle distribution" \
      "https://services.gradle.org/distributions/${GRADLE_DIST}-bin.zip" \
      "services.gradle.org" \
      "mirrors.cloud.tencent.com" "https://mirrors.cloud.tencent.com/gradle/${GRADLE_DIST}-bin.zip" \
      "mirrors.aliyun.com" "https://mirrors.aliyun.com/gradle/${GRADLE_DIST}-bin.zip")
    download_file "$gradle_url" "$GRADLE_ZIP"
  else
    log "Gradle zip already present: $GRADLE_ZIP"
  fi

  if [[ ! -d "$GRADLE_ROOT/$GRADLE_DIST" ]]; then
    log "Extracting Gradle ${GRADLE_VERSION}"
    unzip -q "$GRADLE_ZIP" -d "$GRADLE_ROOT"
  else
    log "Local Gradle already extracted: $GRADLE_ROOT/$GRADLE_DIST"
  fi

  export GRADLE_HOME="$GRADLE_ROOT/$GRADLE_DIST"
  export PATH="$GRADLE_HOME/bin:$PATH"
}

update_gradle_wrapper_properties() {
  local wrapper_file="gradle/wrapper/gradle-wrapper.properties"
  if [[ ! -f "$wrapper_file" ]]; then
    return
  fi
  if [[ ! -f "$GRADLE_ZIP" ]]; then
    log "Gradle zip not found; keeping existing wrapper distributionUrl"
    return
  fi

  local gradle_zip_abs="$GRADLE_ZIP"
  if command_exists readlink; then
    gradle_zip_abs=$(readlink -f "$GRADLE_ZIP" 2>/dev/null || echo "$GRADLE_ZIP")
  fi
  local file_url="file\\://$gradle_zip_abs"

  # 默认**不改**仓库里的 distributionUrl。
  #
  # 早期实现会无条件改成 file:///root/... 这样的绝对路径，而这是受版本控制
  # 的文件：一旦提交，别人 clone 下来 wrapper 直接指向不存在的本地文件。
  # 现有 URL 指向 services.gradle.org，Gradle 会缓存到 GRADLE_USER_HOME，
  # 装了 Gradle 的机器本来就不必改。只有显式设置 EYECARE_FORCE_LOCAL_WRAPPER=1
  # 才改写（例如完全离线、必须用本地 zip 时），并提示需要还原。
  if [[ "${EYECARE_FORCE_LOCAL_WRAPPER:-0}" != "1" ]]; then
    log "Keeping wrapper distributionUrl as-is (set EYECARE_FORCE_LOCAL_WRAPPER=1 for a local zip)"
    return
  fi

  if grep -q '^distributionUrl=' "$wrapper_file"; then
    sed -i "s|^distributionUrl=.*|distributionUrl=$file_url|" "$wrapper_file"
  else
    echo "distributionUrl=$file_url" >> "$wrapper_file"
  fi
  log "Wrapper distributionUrl set to local file: $gradle_zip_abs"
  log "NOTE: this modifies a tracked file; revert it before committing"
}

warmup_gradle_wrapper_cache() {
  if [[ ! -x "./gradlew" ]]; then
    log "gradlew not found; skipping wrapper cache warm-up"
    return 0
  fi
  if [[ ! -f "gradle/wrapper/gradle-wrapper.properties" ]]; then
    log "gradle-wrapper.properties not found; skipping wrapper cache warm-up"
    return 0
  fi
  log "Warming Gradle wrapper cache"
  if ! ./gradlew --version --no-daemon >/dev/null; then
    log "Wrapper cache warm-up failed; continuing"
    return 1
  fi
}

# 补齐 gradle.properties 里必需的 proot 兼容项。
#
# ⚠️ 这里**不能**把整个文件覆盖成模板。早期实现用 heredoc 整份重写，
# 效果是悄悄丢掉了 `android.enableResourceOptimizations=false` ——
# 而 README 明确写了缺这一项在 proot 上会打出缺少 AndroidManifest 的空 APK。
# 它只改写受版本控制的工作区文件，用户提交后没人会发现少了一行。
# 现在改为「缺什么补什么」，只追加缺失的键，已有内容一律不动。
restore_gradle_properties() {
  local props="gradle.properties"
  touch "$props"
  local added=0

  ensure_prop() {
    local key="$1" line="$2"
    if grep -q "^${key}=" "$props"; then
      return 0
    fi
    {
      echo ""
      echo "# added by setup_android_env.sh"
      echo "$line"
    } >> "$props"
    added=$((added + 1))
  }

  ensure_prop "android.useAndroidX" "android.useAndroidX=true"
  ensure_prop "android.nonTransitiveRClass" "android.nonTransitiveRClass=true"
  # Proot/Termux：aapt2 守护进程在该环境无法启动
  ensure_prop "android.aapt2.process.daemon" "android.aapt2.process.daemon=false"
  # PROOT：aapt2 optimize 产出异常，必须关闭资源优化
  ensure_prop "android.enableResourceOptimizations" "android.enableResourceOptimizations=false"

  if [[ "$added" -gt 0 ]]; then
    log "Appended $added missing key(s) to gradle.properties"
  else
    log "gradle.properties already complete"
  fi

  # 历史上这里还会把 gradlew.bat 整份重写。该文件在仓库里本来完好，
  # 无条件重写只会制造无意义的 diff，已移除。
}


update_local_properties() {
  local sdk_dir="$ANDROID_HOME"
  cat > local.properties <<EOF
## This file is automatically generated by Android Studio.
# Do not modify this file -- YOUR CHANGES WILL BE ERASED!
#
# This file should *NOT* be checked into Version Control Systems,
# as it contains information specific to your local configuration.
#
# Location of the SDK. This is only used by Gradle.
# For customization when using a Version Control System, please read the
# header note.
sdk.dir=$sdk_dir
EOF
}

configure_env_persistence() {
  local bashrc="$HOME/.bashrc"
  touch "$bashrc"
  if ! grep -q "operit android env" "$bashrc"; then
    cat >> "$bashrc" <<EOF
# >>> operit android env >>>
export JAVA_HOME=$JAVA_HOME
export ANDROID_HOME=$ANDROID_HOME
export ANDROID_SDK_ROOT=$ANDROID_HOME
export PATH=\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$JAVA_HOME/bin:\$PATH
export GRADLE_USER_HOME=$GRADLE_USER_HOME
export GRADLE_HOME=${GRADLE_HOME:-$HOME/gradle/gradle-9.1.0}
export PATH=\$GRADLE_HOME/bin:\$PATH
# <<< operit android env <<<
EOF
    log "Environment variables appended to ~/.bashrc"
  else
    log "Environment variables already configured in ~/.bashrc"
  fi
}

warmup_gradle_cache_for_aapt2() {
  local gradle_cmd="$GRADLE_HOME/bin/gradle"
  if [[ ! -x "$gradle_cmd" ]]; then
    log "Local Gradle not found: $gradle_cmd"
    return 1
  fi
  log "Running warm-up Gradle task to resolve and execute AAPT2"
  if ! "$gradle_cmd" --no-daemon --rerun-tasks :app:processDebugResources; then
    log "AAPT2 pre-replace warm-up failed; continuing to patch aapt2"
    return 1
  fi
}

warmup_gradle_cache_after_aapt2_replace() {
  local gradle_cmd="$GRADLE_HOME/bin/gradle"
  if [[ ! -x "$gradle_cmd" ]]; then
    log "Local Gradle not found: $gradle_cmd"
    return 1
  fi
  log "Running post-replace warm-up to ensure patched AAPT2 is used"
  if ! "$gradle_cmd" --no-daemon --rerun-tasks :app:processDebugResources; then
    log "AAPT2 post-replace warm-up failed; setup will still continue"
    return 1
  fi
}

replace_aapt2() {
  # 只处理 aarch64 主机：x86-64 主机上 AGP 自带的 aapt2 本来就能跑，
  # 盲目替换反而会把能用的二进制换掉。
  if [[ "$(uname -m)" != "aarch64" && "$(uname -m)" != "arm64" ]]; then
    log "Not an aarch64 host ($(uname -m)); keeping AGP's own aapt2"
    return 0
  fi

  local bundled_aapt2="$SCRIPT_DIR/tools/aapt2/aapt2-arm64-v8a"

  # tools/ 被 .gitignore 排除，从仓库压缩包解出来的副本里根本没有这个二进制。
  # 以前这里直接 fail，整个 setup 到此为止 —— 但 aarch64 的 aapt2 有多种取得方式，
  # 其中 qemu 兜底完全不需要这个文件，不该因为它缺席就中断整个环境搭建。
  if [[ ! -f "$bundled_aapt2" ]]; then
    log "WARNING: bundled ARM64 aapt2 not found: $bundled_aapt2"
    log "         (tools/ is gitignored, so it is absent in source archives)"
    log "         Falling back to the qemu-based aapt2 override; see install_aapt2_qemu_override"
    return 1
  fi

  local expected_sha256="e5b5ff7f0d4f6ecd7fa5d05d77fed3f09f6f1bf80f078b8aada82bc578848561"
  local actual_sha256
  actual_sha256=$(sha256sum "$bundled_aapt2" | awk '{print $1}')
  if [[ "$actual_sha256" != "$expected_sha256" ]]; then
    log "WARNING: Bundled ARM64 aapt2 checksum mismatch: expected $expected_sha256, got $actual_sha256"
    log "         Refusing to use it; falling back to the qemu-based aapt2 override"
    return 1
  fi

  local tmp_dir
  tmp_dir=$(mktemp -d)
  local aapt2_path="$tmp_dir/aapt2"
  log "Using bundled ARM64 aapt2 from template"
  cp "$bundled_aapt2" "$aapt2_path"
  chmod +x "$aapt2_path"

  if [[ -d "$ANDROID_HOME/build-tools/35.0.0" ]]; then
    cp "$aapt2_path" "$ANDROID_HOME/build-tools/35.0.0/aapt2"
    log "Replaced SDK build-tools aapt2"
  fi

  local gradle_cache_root="$GRADLE_USER_HOME/caches"
  local gradle_aapt_dir="$gradle_cache_root/modules-2/files-2.1/com.android.tools.build/aapt2"
  if [[ -d "$gradle_aapt_dir" ]]; then
    local updated_jar_count=0
    while IFS= read -r -d '' jar_path; do
      local jar_dir
      jar_dir=$(dirname "$jar_path")
      cp "$aapt2_path" "$jar_dir/aapt2"
      (cd "$jar_dir" && zip -q -f "$(basename "$jar_path")" aapt2)
      updated_jar_count=$((updated_jar_count + 1))
    done < <(find "$gradle_aapt_dir" -name "aapt2-*-linux.jar" -print0)
    log "Updated Gradle cache aapt2 jars: $updated_jar_count"
  else
    log "Gradle aapt2 module cache not found: $gradle_aapt_dir"
  fi

  local updated_transform_count=0
  while IFS= read -r -d '' transforms_dir; do
    while IFS= read -r -d '' transformed_aapt2; do
      cp "$aapt2_path" "$transformed_aapt2"
      updated_transform_count=$((updated_transform_count + 1))
    done < <(find "$transforms_dir" -name "aapt2" -type f -print0 2>/dev/null || true)
  done < <(find "$gradle_cache_root" -maxdepth 1 -type d -name "transforms-*" -print0 2>/dev/null || true)
  if [[ "$updated_transform_count" -gt 0 ]]; then
    log "Updated transformed aapt2 binaries: $updated_transform_count"
  else
    log "No transformed aapt2 binaries found under: $gradle_cache_root"
  fi

  rm -rf "$tmp_dir"
  return 0
}

# aarch64 主机的 aapt2 兜底方案：用 qemu-user 跑 AGP 自带的 x86-64 aapt2。
#
# 为什么不是下载一个 aarch64 aapt2：
#   - AGP 9 的 aapt2 没有 linux-aarch64 分类器（Maven 上实测 404）；
#   - Termux 的 aapt2 链接 bionic 且依赖一串 libc++/libfmt/libprotobuf，
#     在 glibc 或 proot 里都要额外搬运依赖，脆弱且版本对不上 AGP。
#
# 为什么不是直接替换缓存里的二进制：
#   replace_aapt2 干的就是这件事，但它需要一个被 gitignore 的 tools/ 二进制。
#   AGP 官方支持 android.aapt2FromMavenOverride 指向任意可执行文件，
#   改用户级 ~/.gradle/gradle.properties 即可，不动仓库、不动 Gradle 缓存。
#
# 关键细节：AGP 会校验该路径**以 "aapt2" 结尾**且可执行，
# 所以包装脚本必须放在 bin/aapt2 这样的位置，不能叫 aapt2-qemu。
install_aapt2_qemu_override() {
  local tool_dir="${AAPT2_QEMU_DIR:-$HOME/android-tools}"
  local sdk_aapt2_jar
  sdk_aapt2_jar=$(find "$GRADLE_USER_HOME/caches/modules-2/files-2.1/com.android.tools.build/aapt2" \
    -name "aapt2-*-linux.jar" 2>/dev/null | sort | tail -1)

  if [[ -z "$sdk_aapt2_jar" ]]; then
    log "WARNING: no aapt2-*-linux.jar in Gradle cache yet; run ./gradlew once first, then re-run this script"
    return 1
  fi

  if ! command_exists qemu-x86_64-static; then
    log "Installing qemu-user-static (needed to run the x86-64 aapt2)"
    install_packages qemu-user-static
  fi
  # qemu 跑 x86-64 ELF 需要目标架构的 loader 与 libc
  if [[ ! -e /usr/x86_64-linux-gnu/lib/ld-linux-x86-64.so.2 ]]; then
    log "Installing x86-64 cross libc (loader + libstdc++ for qemu)"
    install_packages libc6-amd64-cross libstdc++6-amd64-cross
  fi

  mkdir -p "$tool_dir/bin"
  local real_bin="$tool_dir/aapt2-x86_64"
  local unzip_dir
  unzip_dir=$(mktemp -d)
  if ! unzip -o -q "$sdk_aapt2_jar" aapt2 -d "$unzip_dir"; then
    log "WARNING: could not extract aapt2 from $sdk_aapt2_jar"
    rm -rf "$unzip_dir"
    return 1
  fi
  cp "$unzip_dir/aapt2" "$real_bin"
  rm -rf "$unzip_dir"
  chmod +x "$real_bin"

  cat > "$tool_dir/bin/aapt2" <<WRAPPER
#!/bin/sh
# aapt2 (x86-64, extracted from AGP) under qemu-user.
# AGP 9 publishes no linux-aarch64 aapt2, and binfmt_misc is usually unavailable
# inside proot, so this wrapper makes the x86-64 binary transparently runnable.
# Injected via android.aapt2FromMavenOverride in \$HOME/.gradle/gradle.properties.
exec /usr/bin/qemu-x86_64-static -L /usr/x86_64-linux-gnu "$real_bin" "\$@"
WRAPPER
  chmod +x "$tool_dir/bin/aapt2"

  if ! "$tool_dir/bin/aapt2" version >/dev/null 2>&1; then
    log "WARNING: qemu aapt2 wrapper did not run; aarch64 AAPT2 is still unresolved"
    return 1
  fi

  local user_gradle_props="$GRADLE_USER_HOME/gradle.properties"
  touch "$user_gradle_props"
  if grep -q "^android.aapt2FromMavenOverride=" "$user_gradle_props"; then
    sed -i "s|^android.aapt2FromMavenOverride=.*|android.aapt2FromMavenOverride=$tool_dir/bin/aapt2|" "$user_gradle_props"
  else
    {
      echo ""
      echo "# aarch64/proot: AGP 9 has no linux-aarch64 aapt2, run the x86-64 one under qemu."
      echo "# Written by setup_android_env.sh."
      echo "android.aapt2FromMavenOverride=$tool_dir/bin/aapt2"
    } >> "$user_gradle_props"
  fi
  log "Configured AAPT2 override: $tool_dir/bin/aapt2 ($("$tool_dir/bin/aapt2" version 2>&1 | head -1))"
  return 0
}

main() {
  SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
  cd "$SCRIPT_DIR"

  if [[ -f "./gradlew" ]]; then
    chmod +x "./gradlew"
  fi

  install_packages wget curl unzip zip
  ensure_ping
  ensure_java
  resolve_java_home
  ensure_android_tools
  ensure_gradle
  update_gradle_wrapper_properties
  if ! warmup_gradle_wrapper_cache; then
    log "Ignoring wrapper warm-up error and continuing"
  fi
  restore_gradle_properties
  update_local_properties
  if ! warmup_gradle_cache_for_aapt2; then
    log "Ignoring pre-replace warm-up error and continuing to patch aapt2"
  fi
  if replace_aapt2; then
    if ! warmup_gradle_cache_after_aapt2_replace; then
      log "Ignoring post-replace warm-up error and continuing"
    fi
  else
    # 没有可用的内置 aarch64 aapt2：走 qemu 兜底（只需 Gradle 缓存里有 AGP 的 aapt2 jar）
    if ! install_aapt2_qemu_override; then
      log "WARNING: AAPT2 on this aarch64 host is still unresolved; see README「aarch64 主机的 AAPT2」"
    fi
  fi
  configure_env_persistence

  log "Android environment setup complete"
  log "Reload shell or run: source ~/.bashrc"
}

main "$@"
