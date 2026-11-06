package dev.questplus.clientmodel;

/** Fixed-size masks keep authored large nodes from creating per-pixel render work. */
public final class QuestShape {
    public record Span(int start,int end,int row) {}
    private static final java.util.Map<String,java.util.List<Span>> MASKS=new java.util.HashMap<>();
    static {
        for(String shape:java.util.List.of("square","circle","rounded_square","diamond","pentagon","hexagon","octagon","heart","gear")) {
            var spans=new java.util.ArrayList<Span>();
            for(int row=0;row<32;row++) {
                int start=-1;
                for(int col=0;col<=32;col++) {
                    boolean filled=col<32&&contains(shape,(col+.5)/16-1,(row+.5)/16-1);
                    if(filled&&start<0)start=col;
                    if(!filled&&start>=0){spans.add(new Span(start,col,row));start=-1;}
                }
            }
            MASKS.put(shape,java.util.List.copyOf(spans));
        }
    }
    public static java.util.List<Span> spans(String shape) {return MASKS.getOrDefault(shape,MASKS.get("square"));}
    public static void install(String name,java.util.List<Span> spans) {
        if(!name.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||spans.isEmpty()||spans.size()>512||spans.stream().anyMatch(s->s.start()<0||s.end()>32||s.start()>=s.end()||s.row()<0||s.row()>31))throw new IllegalArgumentException("Invalid shape mask");
        MASKS.put(name,java.util.List.copyOf(spans));
    }
    public static void removeCustom(String name){if(name.contains(":"))MASKS.remove(name);}
    private QuestShape() {}
    public static boolean contains(String shape,double x,double y) {
        if(Math.abs(x)>1||Math.abs(y)>1)return false;
        if(shape.contains(":")&&MASKS.containsKey(shape)){int col=Math.min(31,(int)((x+1)*16)),row=Math.min(31,(int)((y+1)*16));return MASKS.get(shape).stream().anyMatch(s->s.row()==row&&col>=s.start()&&col<s.end());}
        return switch(shape) {
            case "circle" -> x*x+y*y<=1;
            case "rounded_square" -> Math.pow(Math.abs(x),4)+Math.pow(Math.abs(y),4)<=1;
            case "diamond" -> Math.abs(x)+Math.abs(y)<=1;
            case "heart" -> Math.pow(x*x+y*y-0.65,3)-x*x*Math.pow(-y,3)<=0;
            case "pentagon","hexagon","octagon","gear" -> polygon(shape,x,y);
            default -> true;
        };
    }
    private static boolean polygon(String shape,double x,double y) {
        int n=switch(shape){case "pentagon"->5;case "hexagon"->6;case "octagon"->8;default->24;};
        boolean inside=false;
        for(int i=0,j=n-1;i<n;j=i++) {
            double r=shape.equals("gear")&&i%4>=2?.75:1,prior=shape.equals("gear")&&j%4>=2?.75:1;
            double a=2*Math.PI*i/n-Math.PI/2,b=2*Math.PI*j/n-Math.PI/2;
            double ax=Math.cos(a)*r,ay=Math.sin(a)*r,bx=Math.cos(b)*prior,by=Math.sin(b)*prior;
            if((ay>y)!=(by>y)&&x<(bx-ax)*(y-ay)/(by-ay)+ax)inside=!inside;
        }
        return inside;
    }
}
