package dev.cbui.hud;

public final class CpsCounter {
    private final long[] clicks; private int next,count;
    public CpsCounter(int capacity){clicks=new long[capacity];}
    public void click(long now){clicks[next]=now;next=(next+1)%clicks.length;if(count<clicks.length)count++;}
    public int count(long now){int active=0;for(int i=0;i<count;i++)if(now-clicks[i]<1000L)active++;return active;}
}
