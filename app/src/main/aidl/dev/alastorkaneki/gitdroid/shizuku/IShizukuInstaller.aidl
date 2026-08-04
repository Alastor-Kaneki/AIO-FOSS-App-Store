package dev.alastorkaneki.gitdroid.shizuku;

import android.os.ParcelFileDescriptor;

interface IShizukuInstaller {
    String install(in ParcelFileDescriptor apk, long sizeBytes);
}
