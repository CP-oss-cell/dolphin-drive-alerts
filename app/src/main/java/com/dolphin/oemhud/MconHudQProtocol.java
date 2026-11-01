package com.dolphin.oemhud;

/**
 * Interoperability encoder for the original TPLAY HUD Q / MCon turn frame.
 * Derived independently by examining the existing MCon K 2.1.5.004 DEX
 * Lma/p->p0(ILoa/r;Z): prefix 19 4D 01 05, rotary flag,
 * device turn code, 24-bit big-endian meters, end marker 2F.
 * No MCon source or binaries are bundled in this app.
 */
public final class MconHudQProtocol {
    private MconHudQProtocol() {}
    public static byte[] turn(int code, int distanceMeters, boolean rotary) {
        if (code < 0 || code > 18) throw new IllegalArgumentException("미지원 방향 코드");
        if (distanceMeters < 0 || distanceMeters > 0xffffff) throw new IllegalArgumentException("거리 범위 오류");
        return new byte[]{(byte)0x19,(byte)0x4d,(byte)0x01,(byte)0x05,
                (byte)(rotary?1:0),(byte)code,
                (byte)(distanceMeters >>> 16),(byte)(distanceMeters >>> 8),(byte)distanceMeters,
                (byte)0x2f};
    }
    public static int directionCode(String direction) {
        if(direction==null)return -1;
        String d=direction.replace(" ","");
        if(d.contains("유턴")||d.contains("U턴")||d.contains("U-TURN"))return 7;
        if(d.contains("고속도로") && d.contains("왼쪽") && d.contains("진입"))return 8;
        if(d.contains("고속도로") && d.contains("오른쪽") && d.contains("진입"))return 9;
        if(d.contains("고속도로") && d.contains("왼쪽") && d.contains("진출"))return 10;
        if(d.contains("고속도로") && d.contains("오른쪽") && d.contains("진출"))return 11;
        if(d.contains("좌전방"))return 1;
        if(d.contains("우전방"))return 4;
        if(d.contains("좌후방"))return 3;
        if(d.contains("우후방"))return 6;
        if(d.contains("좌회전") || d.equals("왼쪽")||d.contains("좌측"))return 2;
        if(d.contains("우회전") || d.equals("오른쪽")||d.contains("우측"))return 5;
        if(d.contains("직진"))return 0;
        if(d.contains("회전교차로")||d.contains("로터리"))return -1; // exit number required
        return -1;
    }
    public static byte[] fromCue(NavCue cue){
        if(cue==null||cue.distanceMeters<0)return null;
        int code=directionCode(cue.direction);
        if(code<0) return null;
        return turn(code,cue.distanceMeters,false);
    }
    public static String hex(byte[] bytes){
        if(bytes==null)return "";
        char[] h="0123456789ABCDEF".toCharArray(); char[] s=new char[bytes.length*2];
        for(int i=0;i<bytes.length;i++){int v=bytes[i]&255;s[i*2]=h[v>>>4];s[i*2+1]=h[v&15];}
        return new String(s);
    }
}
