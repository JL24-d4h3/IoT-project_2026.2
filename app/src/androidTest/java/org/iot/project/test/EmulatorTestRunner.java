package org.iot.project.test;

import android.app.Application;
import android.content.Context;

import androidx.test.runner.AndroidJUnitRunner;

/** Sustituye la Application antes de instalar FirebaseInitProvider. */
public final class EmulatorTestRunner extends AndroidJUnitRunner {
    @Override
    public Application newApplication(ClassLoader cl, String className, Context context)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        return super.newApplication(cl, FirebaseEmulatorApplication.class.getName(), context);
    }
}
