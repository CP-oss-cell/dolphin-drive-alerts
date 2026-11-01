package com.dolphin.oemhud;
public final class HudSettingsProtocolTest{
 static int passed=0;
 static void assertThat(boolean v,String desc){if(!v)throw new AssertionError(desc);System.out.println("PASS HUDSET "+desc);passed++;}
 public static void main(String[]args){
  assertThat(MconHudQProtocol.hex(HudSettingsProtocol.buzzer(true,0)).equals("194D080201032F"),"low buzzer");
  assertThat(MconHudQProtocol.hex(HudSettingsProtocol.buzzer(true,1)).equals("194D080201062F"),"normal buzzer");
  assertThat(MconHudQProtocol.hex(HudSettingsProtocol.buzzer(true,2)).equals("194D080201092F"),"high buzzer");
  assertThat(MconHudQProtocol.hex(HudSettingsProtocol.buzzer(false,2)).equals("194D080200002F"),"buzzer off");
  assertThat(MconHudQProtocol.hex(HudSettingsProtocol.brightness(2,0)).equals("194D070202002F"),"day-bright night-dim auto profile");
  assertThat(MconHudQProtocol.hex(HudSettingsProtocol.brightness(1,1)).equals("194D070201012F"),"manual fixed level");
  assertThat(HudSettingsProtocol.isSettingsFrame(HudSettingsProtocol.buzzer(true,1)),"valid buzzer frame");
  assertThat(HudSettingsProtocol.isSettingsFrame(HudSettingsProtocol.brightness(0,2)),"valid luminance frame");
  assertThat(!HudSettingsProtocol.isSettingsFrame(new byte[]{0x19,0x4d,8,2,1,8,0x2f}),"reject undocumented sound level");
  assertThat(!HudSettingsProtocol.isSettingsFrame(new byte[]{0x19,0x4d,7,2,3,0,0x2f}),"reject unsupported brightness level");
  assertThat(!HudSettingsProtocol.isSettingsFrame(new byte[]{0x19,0x4d,9,2,0,0,0x2f}),"reject unknown command");
  assertThat(!HudSettingsProtocol.isSettingsFrame(null),"reject null frame");
  System.out.println("HUD SETTINGS "+passed+" PASS");
 }
}
