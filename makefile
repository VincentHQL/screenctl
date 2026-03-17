# 构建配置
BUILD_TYPE ?= debug
GRADLE_OPTS ?= --no-daemon --stacktrace

# Android SDK 配置
ANDROID_HOME ?= $(shell echo $$ANDROID_HOME)
JAVA_HOME ?= $(shell echo $$JAVA_HOME)

# 输出目录
BUILD_DIR = build
APK_DIR = $(BUILD_DIR)/outputs/apk
RELEASE_DIR = release

# 默认目标
all: build

# 检查环境
check-env:
    @echo "Checking build environment..."
    @if [ -z "$(ANDROID_HOME)" ]; then \
        echo "Error: ANDROID_HOME environment variable is not set"; \
        exit 1; \
    fi
    @if [ ! -d "$(ANDROID_HOME)" ]; then \
        echo "Error: ANDROID_HOME directory does not exist: $(ANDROID_HOME)"; \
        exit 1; \
    fi
    @if [ -z "$(JAVA_HOME)" ]; then \
        echo "Error: JAVA_HOME environment variable is not set"; \
        exit 1; \
    fi
    @echo "Environment check passed"

# 清理
clean:
    @echo "Cleaning build artifacts..."
    ./gradlew clean
    @rm -rf $(RELEASE_DIR)

# 构建 Debug APK
build-debug: check-env
    @echo "Building debug APK..."
    ./gradlew assembleDebug $(GRADLE_OPTS)

# 构建 Release APK
build-release: check-env
    @echo "Building release APK..."
    ./gradlew assembleRelease $(GRADLE_OPTS)

# 默认构建（根据 BUILD_TYPE）
build: check-env
    @echo "Building $(BUILD_TYPE) APK..."
ifeq ($(BUILD_TYPE),release)
    ./gradlew assembleRelease $(GRADLE_OPTS)
else
    ./gradlew assembleDebug $(GRADLE_OPTS)
endif

# 安装到设备
install: build
    @echo "Installing APK to device..."
ifeq ($(BUILD_TYPE),release)
    ./gradlew installRelease $(GRADLE_OPTS)
else
    ./gradlew installDebug $(GRADLE_OPTS)
endif

# 卸载应用
uninstall:
    @echo "Uninstalling app from device..."
    ./gradlew uninstallAll $(GRADLE_OPTS)

# 运行测试
test:
    @echo "Running tests..."
    ./gradlew test $(GRADLE_OPTS)

# 运行 Android 测试
test-android:
    @echo "Running Android tests..."
    ./gradlew connectedAndroidTest $(GRADLE_OPTS)

# 代码检查
lint:
    @echo "Running lint checks..."
    ./gradlew lint $(GRADLE_OPTS)

# 创建发布目录并复制 APK
release: build-release
    @echo "Preparing release..."
    @mkdir -p $(RELEASE_DIR)
    @cp $(APK_DIR)/release/*.apk $(RELEASE_DIR)/
    @echo "Release APK copied to $(RELEASE_DIR)/"

# 签名 APK（需要配置 keystore）
sign-release: build-release
    @echo "Signing release APK..."
    ./gradlew assembleRelease $(GRADLE_OPTS)

# 依赖更新
dependencies:
    @echo "Updating dependencies..."
    ./gradlew dependencies

# 清理并重新构建
rebuild: clean build

# 帮助信息
help:
    @echo "Available targets:"
    @echo "  build         - Build APK (debug/release based on BUILD_TYPE)"
    @echo "  build-debug   - Build debug APK"
    @echo "  build-release - Build release APK"
    @echo "  install       - Install APK to device"
    @echo "  uninstall     - Uninstall app from device"
    @echo "  test          - Run unit tests"
    @echo "  test-android  - Run Android tests"
    @echo "  lint          - Run lint checks"
    @echo "  release       - Create release build and copy to release dir"
    @echo "  bundle        - Build Android App Bundle (AAB)"
    @echo "  clean         - Clean build artifacts"
    @echo "  rebuild       - Clean and rebuild"
    @echo "  help          - Show this help"
    @echo ""
    @echo "Environment variables:"
    @echo "  BUILD_TYPE    - Build type (debug/release), default: debug"
    @echo "  ANDROID_HOME  - Android SDK path"
    @echo "  JAVA_HOME     - Java SDK path"
    @echo "  AVD_NAME      - Emulator name for start-emulator"

.PHONY: all build build-debug build-release install uninstall test test-android lint release sign-release bundle dependencies rebuild clean check-env help