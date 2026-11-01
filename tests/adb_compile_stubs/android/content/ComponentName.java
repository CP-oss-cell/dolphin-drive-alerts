package android.content;public class ComponentName{
 public ComponentName(Context ctx,Class<?> target){}
 public String flattenToString(){return "com.dolphin.oemhud/.Service";}
 public static ComponentName unflattenFromString(String s){return s==null?null:new ComponentName(null,Object.class);}
}
