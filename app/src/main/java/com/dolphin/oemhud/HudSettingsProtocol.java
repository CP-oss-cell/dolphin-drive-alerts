package com.dolphin.oemhud;

/**
 * HUD device configuration frames reconstructed from the user's installed MCon K
 * 2.1.5.004 DEX (THBTManager g0 / Y, Loa/c,d,o enums).
 * Unlike navigation TBT frames these change only HUD display/buzzer settings.
 * Do not use for MOVISION/Rubie or unknown devices. No ECU or OEM writes.
 */
public final class HudSettingsProtocol {
    private HudSettingsProtocol() {}
    private static final int[] VOLUME_CODES = {3, 6, 9}; // low, normal, high
    /** Audible alerts are controlled at the HUD buzzer, not the Android speaker. */
    public static byte[] buzzer(boolean enabled, int level) {
        if(level < 0 || level > 2) throw new IllegalArgumentException("음량 단계 0~2");
        return new byte[]{0x19,0x4d,0x08,0x02,(byte)(enabled?1:0),(byte)(enabled?VOLUME_CODES[level]:0),0x2f};
    }
    /** Brightness is per-day/night 3 step profile. HUD firmware decides day/night. */
    public static byte[] brightness(int day, int night) {
        if(day<0||day>2||night<0||night>2) throw new IllegalArgumentException("밝기 단계 0~2");
        return new byte[]{0x19,0x4d,0x07,0x02,(byte)day,(byte)night,0x2f};
    }
    /** No arbitrary bytes are accepted for the settings transport. */
    public static boolean isSettingsFrame(byte[] f){
        if(f==null||f.length!=7 || (f[0]&255)!=0x19 || (f[1]&255)!=0x4d || (f[3]&255)!=2 || (f[6]&255)!=0x2f)return false;
        int c=f[2]&255, a=f[4]&255,b=f[5]&255;
        if(c==7)return a<=2&&b<=2;
        return c==8 && a<=1 && (a==0 ? b==0 : (b==3||b==6||b==9));
    }
}
