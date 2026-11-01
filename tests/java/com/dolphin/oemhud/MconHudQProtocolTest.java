package com.dolphin.oemhud;
public final class MconHudQProtocolTest {
    private static void expect(String name,String a,String b){if(!a.equals(b))throw new AssertionError(name+" expected="+b+" got="+a);System.out.println("PASS "+name+" "+a);}
    public static void main(String[] args){
        expect("right300",MconHudQProtocol.hex(MconHudQProtocol.turn(5,300,false)),"194D0105000500012C2F");
        expect("left30",MconHudQProtocol.hex(MconHudQProtocol.turn(2,30,false)),"194D0105000200001E2F");
        expect("uturn1200",MconHudQProtocol.hex(MconHudQProtocol.turn(7,1200,false)),"194D010500070004B02F");
        expect("roundaboutflag",MconHudQProtocol.hex(MconHudQProtocol.turn(3,60,true)),"194D0105010300003C2F");
        if(MconHudQProtocol.directionCode("우회전")!=5 || MconHudQProtocol.directionCode("좌회전")!=2 || MconHudQProtocol.directionCode("유턴")!=7 || MconHudQProtocol.directionCode("회전교차로")!=-1)throw new AssertionError("direction codes");
        try{MconHudQProtocol.turn(5,-2,false);throw new AssertionError("negative distance accepted");}catch(IllegalArgumentException ok){System.out.println("PASS input validation");}
        System.out.println("ALL PASS");
    }
}
