# ==============================================================================
# VK Turn VPN Android — Build
# ==============================================================================

GOLIB_DIR = ../vk-turn-ios/golib
AAR_OUTPUT = app/libs/Vpnlib.aar

.PHONY: golib build clean setup

# Собрать Go AAR для Android
golib:
	cd $(GOLIB_DIR) && make android
	cp $(GOLIB_DIR)/Vpnlib.aar $(AAR_OUTPUT)
	@echo ">>> Vpnlib.aar скопирован в app/libs/"

# Собрать APK
build: golib
	./gradlew assembleDebug
	@echo ">>> APK: app/build/outputs/apk/debug/app-debug.apk"

# Установить зависимости
setup:
	cd $(GOLIB_DIR) && make deps

clean:
	./gradlew clean
	rm -f $(AAR_OUTPUT)
