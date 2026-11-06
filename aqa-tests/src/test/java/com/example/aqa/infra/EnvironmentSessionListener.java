package com.example.aqa.infra;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

public class EnvironmentSessionListener implements LauncherSessionListener {

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        TestEnvironment.start();
    }

    @Override
    public void launcherSessionClosed(LauncherSession session) {
        TestEnvironment.stop();
    }
}
