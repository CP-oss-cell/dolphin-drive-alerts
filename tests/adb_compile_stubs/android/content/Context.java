package android.content;import java.io.File;
public class Context {
 public static final String NOTIFICATION_SERVICE="notification";
 public Context getApplicationContext(){return this;}
 public File getFilesDir(){return new File(".");}
 public String getPackageName(){return "com.dolphin.oemhud";}
 public Object getSystemService(String name){return new android.app.NotificationManager();}
 public int checkSelfPermission(String name){return -1;}
 public ContentResolver getContentResolver(){return new ContentResolver();}
}
