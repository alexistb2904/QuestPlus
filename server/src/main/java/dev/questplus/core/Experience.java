package dev.questplus.core;

/** Vanilla cumulative XP, independent of totalExperience (which also tracks previously spent XP). */
public final class Experience {
    private Experience() {}
    public static long base(int level) {
        if(level<0)throw new IllegalArgumentException("Negative XP level");
        double n=level;
        return (long)Math.min(Long.MAX_VALUE,n<=16?n*n+6*n:n<=31?(5*n*n-81*n+720)/2:(9*n*n-325*n+4440)/2);
    }
    public static int capacity(int level){return (int)Math.min(Integer.MAX_VALUE,level>=30?112L+(level-30L)*9:level>=15?37L+(level-15L)*5:7L+level*2L);}
    public static int points(int level,float progress){return (int)Math.min(Integer.MAX_VALUE,base(level)+(long)(progress*capacity(level)));}
    public static int level(int points) {
        if(points<0)throw new IllegalArgumentException("Negative XP points");
        int low=0,high=21864;
        while(low+1<high){int middle=(low+high)/2;if(base(middle)<=points)low=middle;else high=middle;}
        return low;
    }
}
