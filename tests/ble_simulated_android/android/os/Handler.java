package android.os;
import java.util.*;
public class Handler {
 public static long now=0; private static long next=0;
 private static final class Task {final long time,index; final Runnable r; Task(long t,long i,Runnable x){time=t;index=i;r=x;}}
 private static final PriorityQueue<Task> q=new PriorityQueue<>((a,b)->a.time==b.time?Long.compare(a.index,b.index):Long.compare(a.time,b.time));
 public Handler(Looper l){} public boolean post(Runnable r){return postDelayed(r,0);}
 public boolean postDelayed(Runnable r,long ms){q.add(new Task(now+ms,next++,r));return true;}
 public void removeCallbacks(Runnable r){q.removeIf(x->x.r==r);}
 public static void advance(long ms){long end=now+ms;int steps=0;while(!q.isEmpty() && q.peek().time<=end){if(++steps>10000)throw new AssertionError("timer loop"); Task t=q.poll();now=t.time;t.r.run();}now=end;}
 public static void reset(){q.clear();now=0;next=0;}
}
