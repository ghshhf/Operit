# Dev container for ghshhf/Operit — one-command environment.
# Base image ships Android SDK (API 36) + JDK 17. We layer Node + Python
# (needed by web-chat / tooling / sync scripts) and the NDK + CMake required
# by the native modules (quickjs, terminal, ncnn, sherpa-ncnn, llama, MNN).
FROM mcr.microsoft.com/devcontainers/android:0-36

USER root

# Node.js 20 (web-chat / tooling) + Python 3.12 (sync scripts / venv)
RUN export DEBIAN_FRONTEND=noninteractive \
    && apt-get update \
    && apt-get install -y --no-install-recommends \
       nodejs npm python3 python3-pip python3-venv \
    && curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*

# Pre-accept Android SDK licenses and install native build toolchain.
# NDK version is matched to what AGP 8.13 expects; bump if you change AGP.
RUN sdkmanager --licenses >/dev/null 2>&1 || true \
    && sdkmanager \
       "ndk;27.0.12077973" \
       "cmake;3.22.1" \
       "platforms;android-36" \
       "build-tools;36.0.0" || true

WORKDIR /workspace
