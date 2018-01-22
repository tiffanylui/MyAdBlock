package base.tiffany.myadblock;

import android.content.Context;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;
import android.widget.Toast;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public final class FileHelper {

    public static InputStream openRead(Context context, String filename) throws IOException {
        try {
            return context.openFileInput(filename);
        } catch (FileNotFoundException e) {
            return context.getAssets().open(filename);
        }
    }

    private static Configuration readConfigFile(Context context, String name, boolean defaultsOnly) throws IOException {
        InputStream stream;
        if (defaultsOnly) {
            stream = context.getAssets().open(name);
        } else {
            stream = FileHelper.openRead(context, name);
        }

        return Configuration.read(new InputStreamReader(stream));

    }

    public static Configuration loadCurrentSettings(Context context) {
        try {
            return readConfigFile(context, "settings.json", false);
        } catch (Exception e) {
            Toast.makeText(context, context.getString(R.string.cannot_read_config, e.getLocalizedMessage()), Toast.LENGTH_LONG).show();
            return loadPreviousSettings(context);
        }
    }

    public static Configuration loadPreviousSettings(Context context) {
        try {
            return readConfigFile(context, "settings.json.bak", false);
        } catch (Exception e) {
            Toast.makeText(context, context.getString(R.string.cannot_restore_previous_config, e.getLocalizedMessage()), Toast.LENGTH_LONG).show();
            return loadDefaultSettings(context);
        }
    }

    public static Configuration loadDefaultSettings(Context context) {
        try {
            return readConfigFile(context, "settings.json", true);
        } catch (Exception e) {
            Toast.makeText(context, context.getString(R.string.cannot_load_default_config, e.getLocalizedMessage()), Toast.LENGTH_LONG).show();
            return null;
        }
    }

    public static FileDescriptor closeOrWarn(FileDescriptor fd, String tag, String message) {
        try {
            if (fd != null)
                Os.close(fd);
        } catch (ErrnoException e) {
            Log.e(tag, "closeOrWarn: " + message, e);
        } finally {
            return null;
        }
    }

}
