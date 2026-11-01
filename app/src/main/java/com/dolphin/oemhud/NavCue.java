package com.dolphin.oemhud;

import android.os.Bundle;
import java.util.Locale;
import java.util.regex.*;

/** Received guidance only; never writes a vehicle signal. */
public final class NavCue {
    public final String source, direction, road, raw;
    public final int distanceMeters, icon, speed;
    public final long timestamp;
    public NavCue(String src, String dir, int meters, String roadName, int iconId, int kmh, String rawValue) {
        source=src==null?"":src;direction=dir==null?"미확인":dir;distanceMeters=meters;
        road=roadName==null?"":roadName;icon=iconId;speed=kmh;raw=rawValue==null?"":rawValue;
        timestamp=System.currentTimeMillis();
    }
    public String display(){return source+" | "+direction+" | "+(distanceMeters<0?"거리 미확인":distanceMeters+"m")+" | "+road+" | icon="+icon+" | "+speed+"km/h";}
    public boolean isUsable(){return distanceMeters>=0 && distanceMeters<=0xffffff && MconHudQProtocol.directionCode(direction)>=0;}
    static String first(Bundle b,String...keys){
        for(String k:keys){Object o=b.get(k);if(o!=null&&!String.valueOf(o).trim().isEmpty())return String.valueOf(o).trim();}
        return "";
    }
    static int number(Bundle b,int fallback,String...keys){
        for(String k:keys){Object o=b.get(k);if(o!=null)try{return Integer.parseInt(String.valueOf(o).trim());}catch(Exception ignored){}}
        return fallback;
    }
    private static final Pattern DISTANCE=Pattern.compile("(?<![0-9])([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+(?:[.,][0-9]+)?)\\s*(km|㎞|公里|킬로미터|m|米|미터)(?![a-z])",Pattern.CASE_INSENSITIVE);
    static int distanceText(String raw){
        if(raw==null)return -1;
        Matcher m=DISTANCE.matcher(raw);if(!m.find())return -1;
        try{
            String number=m.group(1);
            // '1,200m' is one thousand two hundred metres, not 1.2m.
            // A single comma followed by 1-2 digits is a decimal separator.
            if(number.matches("[0-9]{1,3}(,[0-9]{3})+"))number=number.replace(",", "");
            else number=number.replace(',', '.');
            double amount=Double.parseDouble(number);
            String unit=m.group(2).toLowerCase(Locale.ROOT);
            long meters=Math.round(amount*(unit.equals("km")||unit.equals("㎞")||unit.equals("公里")||unit.equals("킬로미터")?1000:1));
            return meters>=0 && meters<=0xffffff?(int)meters:-1;
        }catch(Exception ignored){return -1;}
    }
    static int distanceValue(Bundle b){
        // Raw meter-valued keys take precedence over formatted numbers such as "1.2km".
        int meters=number(b,-1,"SEG_REMAIN_DIS","SEG_REMAIN_DISTANCE","DISTANCE");
        if(meters>=0)return meters;
        String rawMeters=first(b,"SEG_REMAIN_DIS","SEG_REMAIN_DISTANCE","DISTANCE");
        int rawParsed=distanceText(rawMeters);
        if(rawParsed>=0)return rawParsed;
        int grouped=bareMeters(rawMeters);
        if(grouped>=0)return grouped;
        String display=first(b,"SEG_REMAIN_DIS_AUTO","DISTANCE_TEXT","SEG_DISTANCE_TEXT");
        int parsed=distanceText(display);if(parsed>=0)return parsed;
        // Some OEM broadcasts put a plain numeric meter count in *_AUTO.
        return bareMeters(display);
    }
    static int bareMeters(String value){
        if(value==null)return -1;
        try{
            String n=value.trim();
            if(n.matches("[0-9]{1,3}(,[0-9]{3})+"))n=n.replace(",", "");
            long rawNumber=Long.parseLong(n);
            return rawNumber>=0&&rawNumber<=0xffffff?(int)rawNumber:-1;
        }catch(Exception ignored){return -1;}
    }
    /** AutoNavi/AMap public IconType values. Validate this OEM uses that same profile in real-car logs. */
    public static String fromIcon(int icon){
        switch(icon){
            case 2:return "좌회전";
            case 3:return "우회전";
            // Keep diagonal maneuvers separate: the MCon HUD enum has dedicated
            // LEFT_8/RIGHT_2/LEFT_10/RIGHT_4 codes (verified in supplied DEX).
            case 4:return "좌전방";
            case 5:return "우전방";
            case 6:return "좌후방";
            case 7:return "우후방";
            case 8:return "유턴";
            case 9:return "직진";
            // 0=not navigating, 1=car, 10+=waypoint, roundabout etc; no invented arrows.
            default:return "미확인";
        }
    }
    public static NavCue fromAutonavi(Bundle b){
        if(b==null || number(b,-1,"KEY_TYPE")!=10001)return null;
        int icon=number(b,-1,"NEW_ICON","ICON");
        int meters=distanceValue(b);
        String dir=fromIcon(icon);
        if(dir.equals("미확인")){
            // Honor explicit natural-language direction only when supplied by source.
            String description=first(b,"NEXT_TURN_NAME","NEXT_DIRECTION","NAV_TIP","TURN_TEXT");
            NavCue text=fromText("AutoNavi 텍스트",description);
            if(text!=null){dir=text.direction;if(meters<0)meters=text.distanceMeters;}
        }
        String road=first(b,"NEXT_ROAD_NAME","CUR_ROAD_NAME","EXIT_NAME_INFO");
        int speed=number(b,-1,"CUR_SPEED");
        return new NavCue("AutoNavi 방송",dir,meters,road,icon,speed,"KEY_TYPE=10001");
    }
    private static final Pattern MANEUVER=Pattern.compile(
        "유턴|U\\s*-?\\s*turn|좌회전|우회전|직진|左转|右转|直行|회전교차로|로터리|왼쪽|오른쪽|좌측|우측",
        Pattern.CASE_INSENSITIVE);

    /** Pair the first spoken maneuver with the nearest relevant distance, not with
     * a speed-limit or the distance for a later maneuver in the same notification. */
    public static NavCue fromText(String origin,String value){
        if(value==null)return null;
        String s=value.replace('\n',' ').replaceAll("\\s+"," ").trim();
        if(s.isEmpty())return null;
        Matcher directionMatch=MANEUVER.matcher(s);
        if(!directionMatch.find())return null;
        String keyword=directionMatch.group();
        int at=directionMatch.start(), end=directionMatch.end();
        int nextManeuver=s.length();
        if(directionMatch.find())nextManeuver=directionMatch.start();
        String dir;
        String k=keyword.toLowerCase(Locale.ROOT).replace(" ","");
        if(k.equals("유턴")||k.contains("turn"))dir="유턴";
        else if(k.equals("좌회전")||k.equals("左转"))dir="좌회전";
        else if(k.equals("우회전")||k.equals("右转"))dir="우회전";
        else if(k.equals("직진")||k.equals("直行"))dir="직진";
        else if(k.equals("회전교차로")||k.equals("로터리"))dir="회전교차로";
        else if(k.equals("왼쪽")||k.equals("좌측"))dir="왼쪽";
        else dir="오른쪽";
        int meters=-1, best=Integer.MAX_VALUE;
        Matcher distance=DISTANCE.matcher(s);
        while(distance.find()){
            int dStart=distance.start(), dEnd=distance.end();
            // A distance after a second direction cannot describe the first turn.
            if(dStart>=nextManeuver)continue;
            String between=dEnd<=at?s.substring(dEnd,at):dStart>=end?s.substring(end,dStart):"";
            if(between.length()>36)continue;
            // '우회전 후 300m 좌회전' describes the second maneuver's distance.
            if(dStart>=end && nextManeuver<s.length() && (between.contains("후")||between.contains("이어서")))continue;
            // A speed limit is not a navigation distance (e.g. '제한속도 80km/h').
            String prefix=s.substring(Math.max(0,dStart-7),dStart);
            String suffix=s.substring(dEnd,Math.min(s.length(),dEnd+3));
            if(prefix.contains("속도")||prefix.contains("시속")||suffix.contains("/h")||suffix.contains("/시"))continue;
            int score=dEnd<=at?(at-dEnd):dStart-end+5; // prefer the distance before the arrow
            if(score<best){
                int parsed=distanceText(distance.group());
                if(parsed>=0){meters=parsed;best=score;}
            }
        }
        if(meters<0)return null;
        return new NavCue(origin,dir,meters,s.length()>110?s.substring(0,110):s,-1,-1,"");
    }
}
