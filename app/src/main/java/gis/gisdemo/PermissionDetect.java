package gis.gisdemo;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

public class PermissionDetect {

    /** Determines if the context calling has the required permission
     * @param context - the IPC context
     * @param permission - The permissions to check
     * @return true if the IPC has the granted permission
     */
    public static boolean hasPermission(Context context, String permission) {
        int res = context.checkCallingOrSelfPermission(permission);
        return res == PackageManager.PERMISSION_GRANTED;
    }

    /** Determines if the context calling has the required permissions
     * @param context - the IPC context
     * @param permissions - The permissions to check
     * @return true if the IPC has the granted permission
     */
    public static boolean hasPermissions(Context context, String... permissions) {
        boolean hasAllPermissions = true;
        for(String permission : permissions) {
            if (! hasPermission(context, permission)) {hasAllPermissions = false; }
        }
        return hasAllPermissions;
    }

    public static boolean enforceCallingPermission(Context context,String permission){
        boolean result = true;
        try{
            context.enforceCallingOrSelfPermission(permission,permission +"permisson denied");
        }catch (Exception e){
            e.printStackTrace();
            Log.d("CaptureAct", "enforceCallingPermission: "+e.getMessage());
            result = false;
        }
        Log.d("CaptureAct", "enforceCallingPermission: "+permission +"="+result);
        return result;
    }
}
