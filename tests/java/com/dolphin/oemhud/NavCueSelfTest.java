package com.dolphin.oemhud;
import android.os.Bundle;
public final class NavCueSelfTest {
    private static int count=0;
    private static void ok(boolean condition,String name){if(!condition)throw new AssertionError(name);count++;System.out.println("PASS "+name);}
    private static Bundle tbt(int icon,String dist){Bundle b=new Bundle();b.putInt("KEY_TYPE",10001);b.putInt("NEW_ICON",icon);b.putString("SEG_REMAIN_DIS_AUTO",dist);return b;}
    public static void main(String[]args){
        NavCue a=NavCue.fromText("test","300m 앞에서 우회전하세요");ok(a!=null&&a.isUsable()&&a.distanceMeters==300&&a.direction.equals("우회전"),"text right 300m");
        NavCue b=NavCue.fromText("test","1.2km 앞 좌회전");ok(b!=null&&b.distanceMeters==1200&&b.direction.equals("좌회전"),"text left 1.2km");
        String[] dirs={"좌회전","우회전","좌전방","우전방","좌후방","우후방","유턴","직진"};
        for(int icon=2;icon<=9;icon++){
            NavCue cue=NavCue.fromAutonavi(tbt(icon,"450m"));
            ok(cue!=null&&cue.isUsable()&&cue.direction.equals(dirs[icon-2])&&cue.distanceMeters==450,"autonavi icon "+icon);
            ok(MconHudQProtocol.fromCue(cue)!=null,"HUD encode icon "+icon);
        }
        NavCue none=NavCue.fromAutonavi(tbt(0,"300m"));ok(none!=null&&!none.isUsable(),"cruise/no turn no HUD frame");
        NavCue roundabout=NavCue.fromAutonavi(tbt(11,"120m"));ok(roundabout!=null&&!roundabout.isUsable(),"roundabout no invented direction");
        NavCue meters=NavCue.fromAutonavi(tbt(3,"1.2km"));ok(meters!=null&&meters.distanceMeters==1200,"AUTO distance with unit");
        Bundle raw=tbt(2,"10.2km");raw.putInt("SEG_REMAIN_DIS",340);
        ok(NavCue.fromAutonavi(raw).distanceMeters==340,"prefer raw meters over formatted distance");
        Bundle wrong=tbt(5,"300m");wrong.putInt("KEY_TYPE",9999);ok(NavCue.fromAutonavi(wrong)==null,"broadcast type rejection");
        ok(NavCue.distanceText("not a distance")==-1,"invalid distance rejected");
        ok(NavCue.distanceText("2.5公里")==2500,"Chinese km distance");
        ok(!new NavCue("x","직진",-1,"",9,-1,"").isUsable(),"missing distance fails closed");
        NavCue two=NavCue.fromText("알림","500m 앞 우회전, 이어서 200m 좌회전");
        ok(two!=null&&two.direction.equals("우회전")&&two.distanceMeters==500,"two maneuvers first arrow matches distance");
        NavCue speed=NavCue.fromText("알림","제한속도 80km/h, 300m 앞 좌회전");
        ok(speed!=null&&speed.direction.equals("좌회전")&&speed.distanceMeters==300,"ignore speed-limit before maneuver");
        NavCue reverse=NavCue.fromText("알림","우회전 300m");
        ok(reverse!=null&&reverse.direction.equals("우회전")&&reverse.distanceMeters==300,"direction before distance");
        NavCue mispair=NavCue.fromText("알림","우회전 후 300m 좌회전");
        ok(mispair==null,"avoid pairing second turn distance with first");
        NavCue unsupported=NavCue.fromText("알림","100m 앞 경로 재탐색");
        ok(unsupported==null,"do not infer turn from distance only");
        NavCue multi=NavCue.fromText("알림","200m 앞에서 우회전, 3km 직진");
        ok(multi!=null&&multi.distanceMeters==200&&multi.direction.equals("우회전"),"choose first instruction with two distances");
        ok(NavCue.distanceText("1,200m")==1200,"grouped 1200 metres not 1m");
        ok(NavCue.distanceText("12,345m")==12345,"grouped five digits");
        ok(NavCue.distanceText("1,2km")==1200,"comma decimal km");
        ok(NavCue.distanceText("1,200km")==1200000,"grouped thousands km");
        ok(NavCue.fromAutonavi(tbt(3,"1,200")).distanceMeters==1200,"united bare grouped distance");
        ok(NavCue.distanceText("99999999999999999km")==-1,"overflow rejected");
        ok(MconHudQProtocol.directionCode("좌전방")==1,"diagonal forward-left MCon map");
        ok(MconHudQProtocol.directionCode("우전방")==4,"diagonal forward-right MCon map");
        ok(MconHudQProtocol.directionCode("좌후방")==3,"diagonal back-left MCon map");
        ok(MconHudQProtocol.directionCode("우후방")==6,"diagonal back-right MCon map");
        ok(NavCue.fromText("bad","80km/h 우회전")==null,"speed limit not mistaken for 80km turn");
        Bundle rawGrouped=tbt(3,"");rawGrouped.putString("SEG_REMAIN_DIS","1,200");
        ok(NavCue.fromAutonavi(rawGrouped).distanceMeters==1200,"raw grouped metres without AUTO");
        Bundle fallback=tbt(77,"");fallback.putString("NEXT_TURN_NAME","250m 앞에서 우회전");
        NavCue fallbackCue=NavCue.fromAutonavi(fallback);
        ok(fallbackCue.isUsable()&&fallbackCue.distanceMeters==250&&fallbackCue.direction.equals("우회전"),"OEM text fallback includes distance");
        System.out.println("NavCue tests: "+count+" PASS");
    }
}
