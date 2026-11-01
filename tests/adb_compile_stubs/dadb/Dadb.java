package dadb;
public class Dadb implements java.io.Closeable{
  public static Dadb create(String host,int port,AdbKeyPair keys){return new Dadb();}
  public ShellResponse shell(String command){return new ShellResponse();}
  public void close(){}
}
