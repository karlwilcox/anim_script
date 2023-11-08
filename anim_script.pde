import java.util.Map;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.List;
import java.util.Arrays;
import processing.sound.*;

final boolean DEBUG = true;

/* To run on the command line:
processing-java --sketch=~/Documents/Processing/lego_animation --force --output=~/Documents/Processing/lego_animation/output --run
*/

int FRAMERATE = 10;

HashMap<String,PImage> images = new HashMap<String,PImage>();
HashMap<String,SoundFile> sounds = new HashMap<String,SoundFile>();
ArrayList<Action> actions = new ArrayList<Action>();
SpriteList sprites = new SpriteList();
HashMap<String,String> vars = new HashMap<String,String>();
CommandProcessor commandProcessor;

int thisSecond = 0;
String scriptFile = "script.txt";
final String tokens = " \t,;";

void message(String... messages) {
    if (DEBUG) {
        for(int i = 0; i < messages.length; i++) {
            print(messages[i] + " ");
        }
        println("");
    }
}

/*************************************************************************************************

   ###### #    #   ##   #
   #      #    #  #  #  #
   #####  #    # #    # #
   #      #    # ###### #
   #       #  #  #    # #
   ######   ##   #    # ######

**************************************************************************************************/
// From: https://stackoverflow.com/questions/3422673/how-to-evaluate-a-math-expression-given-in-string-form
double eval(final String str) {
    return new Object() {
        int pos = -1, ch;

        void nextChar() {
            ch = (++pos < str.length()) ? str.charAt(pos) : -1;
        }

        boolean eat(int charToEat) {
            while (ch == ' ') nextChar();
            if (ch == charToEat) {
                nextChar();
                return true;
            }
            return false;
        }

        double parse() {
            nextChar();
            double x = parseExpression();
            if (pos < str.length()) throw new RuntimeException("Unexpected: " + (char)ch);
            return x;
        }

        // Grammar:
        // expression = term | expression `+` term | expression `-` term
        // term = factor | term `*` factor | term `/` factor
        // factor = `+` factor | `-` factor | `(` expression `)` | number
        //        | functionName `(` expression `)` | functionName factor
        //        | factor `^` factor

        double parseExpression() {
            double x = parseTerm();
            for (;;) {
                if      (eat('+')) x += parseTerm(); // addition
                else if (eat('-')) x -= parseTerm(); // subtraction
                else return x;
            }
        }

        double parseTerm() {
            double x = parseFactor();
            for (;;) {
                if      (eat('*')) x *= parseFactor(); // multiplication
                else if (eat('/')) x /= parseFactor(); // division
                else if (eat('>')) x = x > parseFactor() ? 1.0 : 0.0; // greater than
                else if (eat('<')) x = x < parseFactor() ? 1.0 : 0.0; // less than
                else if (eat('=')) x = x == parseFactor() ? 1.0 : 0.0; // equals - use with care
                else return x;
            }
        }

        double parseFactor() {
            if (eat('+')) return +parseFactor(); // unary plus
            if (eat('-')) return -parseFactor(); // unary minus

            double x;
            int startPos = this.pos;
            if (eat('(')) { // parentheses
                x = parseExpression();
                if (!eat(')')) throw new RuntimeException("Missing ')'");
            } else if ((ch >= '0' && ch <= '9') || ch == '.') { // numbers
                while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                x = Double.parseDouble(str.substring(startPos, this.pos));
            } else if (ch >= 'a' && ch <= 'z') { // functions
                while (ch >= 'a' && ch <= 'z') nextChar();
                String func = str.substring(startPos, this.pos);
                if (eat('(')) {
                    x = parseExpression();
                    if (!eat(')')) throw new RuntimeException("Missing ')' after argument to " + func);
                } else {
                    x = parseFactor();
                }
                if (func.equals("sqrt")) x = Math.sqrt(x);
                else if (func.equals("sin")) x = Math.sin(Math.toRadians(x));
                else if (func.equals("cos")) x = Math.cos(Math.toRadians(x));
                else if (func.equals("tan")) x = Math.tan(Math.toRadians(x));
                else throw new RuntimeException("Unknown function: " + func);
            } else {
                throw new RuntimeException("Unexpected: " + (char)ch);
            }

            if (eat('^')) x = Math.pow(x, parseFactor()); // exponentiation

            return x;
        }
    }.parse();
}

/*************************************************************************************************
   #######                        
      #    # #    # ###### #####  
      #    # ##  ## #      #    # 
      #    # # ## # #####  #    # 
      #    # #    # #      #####  
      #    # #    # #      #   #  
      #    # #    # ###### #    # 
                                  
**************************************************************************************************/

class Timer {
    int hour = 0;
    int minute = 0;
    int second = 0;
    private int startTime;

    Timer() {
        startTime = millis();
    }

    void update() {
        int elapsed = millis() - startTime;
        // message(String.valueOf(elapsed));
        hour = elapsed / (1000 * 60 * 60);
        elapsed -= hour * 1000 * 60 * 60;
        minute = elapsed / (1000 * 60);
        elapsed -= minute * 1000 * 60;
        second = elapsed / 1000;
    }

    void reset() {
        startTime = millis();
    }
}

class Now extends Timer {
    Now()  {
        ;
    }

     void update() {
        hour = hour();
        minute = minute();
        second = second();
     }

     void reset() {
        ;
     }
}

/*************************************************************************************************
   #######                  #####                       
      #    # #    # ###### #     #  ####  #####  ###### 
      #    # ##  ## #      #       #    # #    # #      
      #    # # ## # #####  #       #    # #    # #####  
      #    # #    # #      #       #    # #    # #      
      #    # #    # #      #     # #    # #    # #      
      #    # #    # ######  #####   ####  #####  ###### 
                                                        
**************************************************************************************************/

class TimeCode {
    Match hour;
    Match minute;
    Match second;

    void  setFromDuration(List<String> words, String notGiven) {
        String h = notGiven;
        String m = notGiven;
        String s = notGiven;

        int wordPos = 0;
        String value;
        do {
            value = "";
            String word = words.get(wordPos);
            if (word.matches("\\d+")) {
                value = word;
                // got a number, look for a unit
                wordPos += 1;
                if (wordPos < words.size()) {
                    boolean found = false;
                    switch(words.get(wordPos).toLowerCase()) {
                        case "s":
                        case "sec":
                        case "secs":
                        case "second":
                        case "seconds":
                            found = true;
                            s = value;
                            break;
                        case "m":
                        case "min":
                        case "mins":
                        case "minute":
                        case "minutes":
                            found = true;
                            m = value;
                            break;
                        case "h":
                        case "hr":
                        case "hrs":
                        case "hour":
                        case "hours":
                            found = true;
                            h = value;
                            break;
                        default:
                            found = false;
                            s = value;
                            break;
                    }
                    if (found) {
                        wordPos += 1;
                        if (wordPos < words.size()) {
                            String andWord = words.get(wordPos).toLowerCase();
                            if (andWord.equals("and") || andWord.equals("&")) {
                                wordPos += 1;
                            }
                        }
                    }
                } else {
                    s = value;
                }
            }
        } while (value != "" && wordPos < words.size());
        hour = new Match(h);
        minute = new Match(m);
        second = new Match(s);
    }



    TimeCode(String timecode) {
        this(timecode,"*");
    }
    
    TimeCode(List<String> words, String notGiven) {
        if (words.get(0).matches(".*:.*")) {
            setFromCode(words.get(0), notGiven);
            return;
        } // else
        setFromDuration(words, notGiven);
    }

    TimeCode(String timecode, String notGiven) {
        setFromCode (timecode, notGiven);
    }

    void setFromCode(String timecode, String notGiven) {
        String[] units = splitTokens(timecode, ":");
        String h = notGiven;
        String m = notGiven;
        String s = notGiven;
        switch (units.length) {
            case 1:
                s = units[0];
                break;
            case 2:
                m = units[0];
                s = units[1];
                break;
            default:
                h = units[0];
                m = units[1];
                s = units[2];
                break;
        }
        hour = new Match(h);
        minute = new Match(m);
        second = new Match(s);
    }

    boolean matches(Timer timer) {
        timer.update();
        return (hour.matches(timer.hour) && minute.matches(timer.minute) && second.matches(timer.second));
    }

    boolean after(Timer timer) {
        timer.update();
        // message(timer.hour + ":" + timer.minute + ":" + timer.second);
        return (hour.after(timer.hour) && minute.after(timer.minute) && second.after(timer.second));
    }

}

/*************************************************************************************************
   #     #                            
   ##   ##   ##   #####  ####  #    # 
   # # # #  #  #    #   #    # #    # 
   #  #  # #    #   #   #      ###### 
   #     # ######   #   #      #    # 
   #     # #    #   #   #    # #    # 
   #     # #    #   #    ####  #    # 
                                      
**************************************************************************************************/

class Match {
    /* How time matching works.
     * There are (currently) 3 fields, H, M, S, each field can be one of 4 types of test value
     * - A number: will be matched against the current unit, true if equal
     * - *: (asterisk) always true
     * - /: (forward slash) never executed, but action still created
     * - %<number>: (percent symbol followed by digits) - see below:
     *      interpreted as a %-age chance, tested once per time unit,
     *      e.g. %10 in the H column is tested on the hour (minute 0) and is true 10% of the time
     *      e.g. %1 in the S column is tested every second and is true 1% of the time
     *
     * These features can be combined, for example
     * 1 %5 * <do something>
     * means that something might be done once each minute for the first hour with a 5% chance,
     * so it will be tested 60 times, at 1 in 20, so probably 3 times or so during the first hour
     *
     * Also note that time matches of:
     * 0 0 0
     * are executed once at start up and not stored (since they are never true again) This
     * can be used to load images for example.
     */
    // match types
    private final int TIME = 0;    // match elapsed time to value
    private final int ALWAYS = 1;  // always matches
    private final int NEVER = 2;   // never matches (i.e. disabled or done)
    private final int PERCENT = 3; // true for value percent of occaisions

    public static final int HOUR = 0;
    public static final int MINUTE = 1;
    public static final int SECOND = 2;

    int value;
    int type;
    String original;

    Match(String pattern) {
        original = pattern;
        switch (pattern.charAt(0)) {
            case '*':
                value = 0;    // not used
                type = ALWAYS;
                break;
            case '/':
                value = 0;    // not used
                type = NEVER;
                break;
            case '%':
                value = int(pattern.substring(1));
                type = PERCENT;
                break;
            default:
                value = int(pattern);
                type = TIME;
                break;
        }
    }

    boolean matches() {
        return matches(-1);
    }

    boolean after(int time) {
        // only really makes sense for actual times
        if (type == TIME) {
            return (time >= value);
        } // else
        return true;
    }

    boolean matches(int time) {
        boolean match = false;
        switch (type) {
            case ALWAYS:
                match = true;
                break;
            case NEVER:
                break;
            case PERCENT:
                match = (int(random(100)) > (100 - value));
                break;
            default:
                match = (time == value);
                break;
        }
        // message ("Comparing " + value + " with " + time + " based on " + original);
        return match;
    }
}

/*************************************************************************************************

      #                                 
     # #    ####  ##### #  ####  #    # 
    #   #  #    #   #   # #    # ##   # 
   #     # #        #   # #    # # #  # 
   ####### #        #   # #    # #  # # 
   #     # #    #   #   # #    # #   ## 
   #     #  ####    #   #  ####  #    # 
                                        
**************************************************************************************************/

class Action {
    Trigger trigger = new Trigger();
    boolean complete = false;

    // public variables
    public String task;
    public String args;
    protected String condition;
    public int line = 0;

    Action(WordList words) {
        int argPos = 0;
        StringBuilder cond = new StringBuilder();
        if (words.size() < 1 || words.get(argPos).equals("")) {
            message("Expected task for trigger");
        } else {
            if (words.get(argPos).equals("if")) {
                argPos++; // go past the if
                boolean found = false;
                while (argPos < words.size()) {
                    String endif = words.get(argPos);
                    argPos++;
                    if (endif.equals("then") || endif.equals("do")) {
                        found = true;
                        break;
                    } else {
                        cond.append(endif + " ");
                    }
                }
                if (!found) {
                    message("Expected then after if");
                }
            }
            condition = cond.toString();
            task = words.get(argPos++);
            if (argPos < words.size()) {
                args = words.getRestAsStr(argPos);
            }
        }
    }

    boolean triggered() {
        return (trigger.triggered() && conditionTrue());
    }

    boolean conditionTrue() {
        boolean outcome = true;
        boolean expressionFound = true;
        String cond = expandAllOnLine(condition);
        
        if (cond.length() < 1) {
            return outcome;
        } // else
        // Try to evaluate it as an expression
        try {
            outcome = (eval(cond) != 0.0);  
        } catch(RuntimeException e) { 
            expressionFound = false;
        }

        if (!expressionFound) { // else compare as strings
            WordList condParts = new WordList(cond, tokens);
            String rhs = null;
            String comparison = null;
            String lhs = condParts.get(0);
            if (condParts.size() > 1) {
                comparison = condParts.get(1);
            }
            if (condParts.size() > 2) {
                rhs = condParts.get(2);
                switch(comparison) {
                    case "=":
                    case "==":
                    case "is":
                    case "equals":
                        outcome = (lhs.equals(rhs));
                        break;
                    case "!=":
                    case "<>":
                        outcome = !(lhs.equals(rhs));
                        break;
                    default:
                        message("Can only compare strings for equality: " + lhs + " " + rhs);
                        break;
                }
            } else { // juse one string
                outcome = lhs.toLowerCase().equals("true");
            }
        }
        if (outcome == false) {
            // for some triggers, we don't test again after the first try
            String triggerType = trigger.getClass().getSimpleName();
            if (triggerType.equals("After") || triggerType.equals("AtTime") || triggerType.equals("Start")) {
               complete = true;
            }
        }
        return outcome;
    }

    void setTrigger(Trigger in_trigger) {
        trigger = in_trigger;
    }

    void dumpAction() {
        String status = complete ? "(completed)" : "available";
        println(task + ": " + String.join(" ", args) + status
            + " Trigger: " + trigger.getClass().getSimpleName());
    }

}

/*************************************************************************************************

    #####                               
   #     # #####  #####  # ##### ###### 
   #       #    # #    # #   #   #      
    #####  #    # #    # #   #   #####  
         # #####  #####  #   #   #      
   #     # #   #  #      #   #   #      
    #####  #    # #      #   #   ###### 
                                        
**************************************************************************************************/


class Sprite {
    String imageTag;
    String tag;
    String scene;
    private boolean visible = false;
    // current values
    float x;      // position and depth
    float y;
    int z;
    float w;    // width and height
    float h;
    float u;    // centre of rotation
    float v;
    float r;    // angle of rotation
    float tint = 0;  // tint and transparency
    float trans = 100;
    // target values
    float tx;
    float ty;
    float tr;
    float tw;
    float th;
    float ttint = 0;
    float ttrans = 0;
    // change rates (per frame)
    float dx = 0;
    float dy = 0;
    float dw = 0;
    float dh = 0;
    float dr = 0;
    float dtint = 0;
    float dtrans = 0;

    public Sprite(String in_imageTag, String in_tag, String in_scene) {
        this(in_imageTag, in_tag, 0.0, 0.0, 0, in_scene);
    }

    public Sprite(String in_imageTag, String  in_tag, float in_x, float in_y, int in_z, String in_scene) {
        this(in_imageTag, in_tag, in_x, in_y, in_z, -1.0, -1.0, in_scene);
    }

    public Sprite(String in_imageTag, String in_tag, float in_x, float in_y, int in_z, float in_w, float in_h, String in_scene) {
        scene = in_scene;
        x = in_x; tx = in_x;
        y = in_y; ty = in_y;
        z = in_z;
        if (in_w < 0.0) {
          PImage image = images.get(in_imageTag);
          in_w = image.width;
          in_h = image.height;
        }
        w = in_w; tw = in_w;
        h = in_h; th = in_h;
        imageTag = in_imageTag;
        tag = in_tag;
    }

    void fade(float to_trans, float in_seconds) {
        // silently set some limits
        if (to_trans > 100) {
            to_trans = 100;
        } else if (to_trans < 0) {
            to_trans = 0;
        }
        if (in_seconds < 0.001) { // move now
            trans = to_trans; ttrans = to_trans;
            return;
        } // else
        ttrans = to_trans;
        dtrans = (to_trans - trans) / (in_seconds * FRAMERATE);
    }
 
    void move(float to_x, float to_y, float in_seconds) {
        // message("Moving " + tag + " to " + to_x + "," + to_y);
        if (in_seconds < 0.001) { // move now
            x = to_x; tx = to_x;
            y = to_y; ty = to_y;
            return;
        } // else
        tx = to_x;
        dx = (to_x - x) / (in_seconds * FRAMERATE);
        ty = to_y;
        dy = (to_y - y) / (in_seconds * FRAMERATE);
    }

    void resize(float to_w, float to_h, float in_seconds) {
        // message("Moving " + tag + " to " + to_x + "," + to_y);
        if (in_seconds < 0.001) { // move now
            w = to_w; tw = to_w;
            h = to_h; th = to_h;
            return;
        } // else
        tw = to_w;
        dw = (to_w - w) / (in_seconds * FRAMERATE);
        th = to_h;
        dh = (to_h - h) / (in_seconds * FRAMERATE);
    }

    void turn(float to_r, float in_seconds) {
        if (in_seconds < 0.001) { // move now
            r = to_r; tr = to_r;
            return;
        } // else
        tr = to_r;
        dr = (to_r - r) / (in_seconds * FRAMERATE);
    }

    void show() {
        visible = true;
    }

    void hide() {
        visible = false;
    }

    boolean isVisible() {
        return visible;
    }

    void update() {
        if (Math.abs(x - tx) > Math.abs(dx)) {
            x += dx;
        }
        if (Math.abs(y - ty) > Math.abs(dy)) {
            y += dy;
        }
        if (Math.abs(w - tw) > Math.abs(dw)) {
            w += dw;
        }
        if (Math.abs(h - th) > Math.abs(dh)) {
            h += dh;
        }
        if (Math.abs(trans - ttrans) > Math.abs(dtrans)) {
            trans += dtrans;
        }
        if (Math.abs(r - tr) > Math.abs(dr)) {
            r += dr;
        } else {
            r = tr;
        }
    }

    void display() {
        if (trans > 0) {
            tint(100, (int)trans);
        }
        if (r != 0.0) {
            pushMatrix();
            translate(x, y);
            rotate(radians(r));
            image(images.get(imageTag), 0, 0, w, h);
            popMatrix();
        } else { // more simple
            image(images.get(imageTag), x, y, w, h);
        }
        if (trans > 0) {
            tint(100, 100);
        }
    }
}

/*************************************************************************************************

    #####                               #                      
   #     # #####  #####  # ##### ###### #       #  ####  ##### 
   #       #    # #    # #   #   #      #       # #        #   
    #####  #    # #    # #   #   #####  #       #  ####    #   
         # #####  #####  #   #   #      #       #      #   #   
   #     # #      #   #  #   #   #      #       # #    #   #   
    #####  #      #    # #   #   ###### ####### #  ####    #   
                                                               
**************************************************************************************************/

class SpriteList {
    private LinkedList<Sprite> spriteList;

    SpriteList() {
        spriteList = new LinkedList<Sprite>();
    }

    ListIterator<Sprite> each() {
        return spriteList.listIterator(0);
    }

    void add(Sprite sprite) {
        int i;
        for (i = 0; i < spriteList.size(); i++) {
            if (sprite.z > spriteList.get(i).z) {
                break;
            }
        }
        spriteList.add(i, sprite);
    }

    void moveTo(int from, int to) {
        if (from < 0 || to < 0 || from == to) {
            return;
        } // else
        Sprite sprite = spriteList.get(from);
        message("Movin ", sprite.tag, " to ", String.valueOf(to));
        // need to find proper z value, make it same as previous
        sprite.z = (to > 0) ? spriteList.get(to -1).z : spriteList.get(0).z;
        spriteList.add(to, sprite);
        spriteList.remove(from);
    }

    void setZ(Sprite sprite, int in_z) {
        for (int i = 0; i < spriteList.size(); i++) {
           if (spriteList.get(i) == sprite) {
               sprite.z = in_z;
               spriteList.remove(i);
               add(sprite);
               return;
           }
        }
    }

    void moveBy(Sprite sprite, int count) {
        int moveTo = -1;
        int moveFrom = -1;
        int size = spriteList.size();
        for (int i = 0; i < size; i++) {
            if (spriteList.get(i) == sprite) {
                // we have our sprite
                moveFrom = i;
                // don't overflow
                if (count < 0) {
                    moveTo = (i + count) > 0 ? (i + count) : 0;
                } else {
                    moveTo = (size < i + count) ? i + count : size - 1;
                }
                moveTo(moveFrom, moveTo);
                return;
            }
        }
    }

    void remove(String in_tag) {
        for (int i = 0; i < spriteList.size(); i++) {
            if (in_tag.equals(spriteList.get(i).tag)) {
                spriteList.remove(i);
                break;
            }
        }
    }

    public Sprite find(String in_tag, String in_scene) {
        Sprite sprite;
        // look for a local tag first
        if ((in_tag.indexOf(":") < 0) && !in_scene.equals("")) {
            String localTag = in_scene + ":" + in_tag;
            for (int i = 0; i < spriteList.size(); i++) { 
                sprite = spriteList.get(i);
                if (localTag.equals(sprite.tag)) {
                    return sprite;
                }
            }
        } // not an error if not found, look globally
        for (int i = 0; i < spriteList.size(); i++) { 
            sprite = spriteList.get(i);
            if (in_tag.equals(sprite.tag)) {
                return sprite;
            }
        }
        // Okay, *now* its an error
        message("Sprite not found: " + in_tag);
        return null;
    }

    public void dumpSprites() {
        Sprite sprite;
        for (int i = 0; i < spriteList.size(); i++) {
            sprite = spriteList.get(i);
            String visibility = " (NOT visible)";
            if (sprite.isVisible()) {
                visibility = " (visible)";
            }
            String inScene = " in top level ";
            if (!sprite.scene.equals("")) {
                inScene = " in scene " + sprite.scene;
            }
            message(sprite.tag + " => " + sprite.imageTag + " in scene " + sprite.scene + visibility, 
                    String.valueOf(sprite.z));
        }
    }

    public void stopScene(String in_scene) {
        Sprite sprite;
        if (in_scene.equals("")) {
            return;
        }
        for (int i = spriteList.size() -1; i >= 0; i--) {
            sprite = spriteList.get(i);
            if (sprite.scene.equals(in_scene)) {
                spriteList.remove(i);
            }
        }
    }
}

void drawSprites() {
    Sprite sprite;
    background(255); // white, for now, TODO make it selectable
    ListIterator<Sprite> iter = sprites.each();
    while (iter.hasNext()) {
        sprite = iter.next();
        sprite.update();
        if (sprite.isVisible()) {
            sprite.display();
        }
    }
}

/*************************************************************************************************

   #     #                      #                      
   #  #  #  ####  #####  #####  #       #  ####  ##### 
   #  #  # #    # #    # #    # #       # #        #   
   #  #  # #    # #    # #    # #       #  ####    #   
   #  #  # #    # #####  #    # #       #      #   #   
   #  #  # #    # #   #  #    # #       # #    #   #   
    ## ##   ####  #    # #####  ####### #  ####    #   
                                                       
**************************************************************************************************/

class WordList {
    private ArrayList<String> words = new ArrayList<String>();
    String defaultValue = "";

    WordList(String[] list) {
        for (int i = 0; i < list.length; i++) {
            words.set(i, list[i]);
        }
    }

    WordList(ArrayList<String> list) {
        words = list;
    }

    WordList(String string, String pattern) {
        this(string, pattern, "");
    }

    WordList(String string, String pattern, String in_default) {
        String[] list = splitTokens(string, pattern);
        for (int i = 0; i < list.length; i++) {
            words.add(i, list[i]);
        }
        defaultValue = in_default;
    }

    String get(int i) {
        if (i < words.size()) {
            return words.get(i);
        } // else
        return defaultValue;
    }

    void put(int i, String content) {
        if (i < words.size()) {
            words.set(i, content);
        }
    }

    ArrayList<String> from(int i) {
        if (i < words.size()) {
            return new ArrayList<String>(words.subList(i, words.size()));
        } // else
        return new ArrayList<String>();
    }

    String getRestAsStr(int from) {
        String rest = "";
        for (int i = from; i < words.size(); i++) {
            rest = rest + words.get(i);  
            if (i < words.size() - 1) {
                rest = rest + " ";
            }
        }
        return rest;
    }

    int size() {
        return words.size();
    }
}


/*************************************************************************************************

   #######                                      
      #    #####  #  ####   ####  ###### #####  
      #    #    # # #    # #    # #      #    # 
      #    #    # # #      #      #####  #    # 
      #    #####  # #  ### #  ### #      #####  
      #    #   #  # #    # #    # #      #   #  
      #    #    # #  ####   ####  ###### #    # 
                                                
**************************************************************************************************/

class Trigger {
    TimeCode timecode;
    Timer timer;

    boolean triggered() {
        return false;
    }

    void reset() {
        if (timer != null) {
            timer.reset();
        }
    }
}

class Start extends Trigger {

    boolean triggered() {
        return true;
    }
}

class OnKey extends Trigger {
    char triggerKey;

    OnKey(String word) {
        triggerKey = word.charAt(0);
    }

    boolean triggered() {
        return (keyPressed && (triggerKey == key));
    }
}

class After extends Trigger {

    After(List words) {
        timer = new Timer();
        timecode = new TimeCode(words, "0");
    }

    boolean triggered() {
        return timecode.after(timer);
    }
}

class Every extends Trigger {
    Every(String word) {
        timer = new Timer();
        timecode = new TimeCode(word, "*");
    }

    boolean triggered() {
        boolean value = timecode.matches(timer);
        timer.reset();
        return value;
    }
}

class AtTime extends Trigger {
    AtTime(String word) {
        timer = new Now();
    }
}

/*************************************************************************************************

   ######                                     
   #     #   ##   #####    ##   #    #  ####  
   #     #  #  #  #    #  #  #  ##  ## #      
   ######  #    # #    # #    # # ## #  ####  
   #       ###### #####  ###### #    #      # 
   #       #    # #   #  #    # #    # #    # 
   #       #    # #    # #    # #    #  ####  
                                              
**************************************************************************************************/

class ParamList {
    HashMap<String, String> params = new HashMap<String, String>();

    ParamList(WordList words, String format) {
        // parameter format is pairs of optionality/name
        final char OPTIONAL = '?'; // create a parameter 'name', dummy value if not present
        final char REQUIRED = '+'; // create a parameter 'name', error if not present
        final char MUSTMATCH = '='; // must be present, but no parameter created
        final char IFMATCHED = '&'; // create parameter 'name', dummy parameter if not CANMATCH previously found
        final char CANMATCH = '~'; // if present, nothing created but flag set for the above
        final char GETREST = '*';  // create a parameter  'name' containing all the rest of the arguments
        final char CHOICE = '|';   // create a parameter containing the first name, value the matched name

        String[] paramList = splitTokens(format," ");
        int argPos = 0;
        boolean matchFound = false;
        for(String paramItem : paramList) {
            String[] parts = splitTokens(paramItem, "/");
            if (parts.length != 2) {
                message("Bad parameter format: " + paramItem);
                continue;
            }
            char optionality = parts[0].charAt(0);
            String name = parts[1];
            String[] choices = null;
            if (optionality == GETREST) {
                params.put(name,words.getRestAsStr(argPos));
                return;
            }
            if (optionality == IFMATCHED && matchFound == false) {
                // only look for this if the previous CANMATCH was found
                params.put(name, "");
                continue;
            }
            if (optionality == CHOICE) {
                choices = splitTokens(name,"|");
                name = choices[0];
            }
            int step = 1;
            if (argPos < words.size()) { // argument is present
                String arg = words.get(argPos);
                switch (optionality) {
                    case OPTIONAL:
                    case REQUIRED:
                        matchFound = false;
                        params.put(name, arg);
                        break;
                    case IFMATCHED:
                        matchFound = false;
                        params.put(name, arg);
                        break;
                    case MUSTMATCH:
                        matchFound = false;
                        if (!arg.equals(name)) {
                            message("Expected: " + name);
                        } // else
                        // do nothing
                        break;
                    case CANMATCH:
                        if (arg.equals(name)) {
                            matchFound = true;
                        } else {
                            step = 0;
                        }
                        break;
                    case CHOICE:
                        for(String choice : choices) {
                            if (arg.equals(choice)) {
                                params.put(name,choice);
                                matchFound = true;
                                break;
                            }
                        }
                        break;
                    default:
                        message("Bad optionality: " + optionality);
                        break;
                }
                argPos += step;
            } else { // no input found
                matchFound = false;
                switch(optionality) {
                    case REQUIRED:
                    case IFMATCHED:
                        message("Expected value for: " + name);
                        params.put(name, "");
                        break;
                    case MUSTMATCH:
                        message("Expected word: " + name);
                        break;
                    case CANMATCH:
                        // do nothing
                        break;
                    case OPTIONAL:
                    case CHOICE:
                        params.put(name, "");
                        break;
                }
            }
        }
    }

    protected void put(String key, String value) {
        if (value == null) {
            value = "";
        }
        params.put(key,value);
    }

    protected String get(String key) {
        if (params.containsKey(key)) {
            return params.get(key);
        } else {
            message("(script) Param key value not found: " + key);
            dumpParams();
            return "";
        }
    }

    protected boolean exists(String key) {
        if (params.containsKey(key)) {
            return true;
        } else {
            message("(script) Param key value not found: " + key);
            return false;
        }
    }

    boolean isEmpty(String key) {
        return (params.containsKey(key) && params.get(key).equals(""));
    }

    int asInt(String key) {
        return asInt(key,0);
    }

    int asInt(String key, int notGiven) {
        String string = get(key);
        if (string.equals("")) {
            return notGiven; // not a error, just not provided
        }
        if (!string.matches("\\d+")) {
            message("Expected integer: " + string);
            return notGiven;
        } // else
        return (Integer.parseInt(string));
    }

    float asFloat(String key) {
        String string = get(key);
        if (string.equals("")) {
            return 0.0; // not a error, just not provided
        }
        if (!(string.matches("-?\\d+(\\.\\d+)?"))) {
            message("Expected integer: " + string);
            return 0.0;
        } // else
        return Float.parseFloat(string);
    }

    public void dumpParams() {
        for(Map.Entry me : params.entrySet()) {
            println(me.getKey() + " -> " + me.getValue());
        }
    }


}

/*************************************************************************************************

    #####                                            
   #     #  ####  #    # #    #   ##   #    # #####  
   #       #    # ##  ## ##  ##  #  #  ##   # #    # 
   #       #    # # ## # # ## # #    # # #  # #    # 
   #       #    # #    # #    # ###### #  # # #    # 
   #     # #    # #    # #    # #    # #   ## #    # 
    #####   ####  #    # #    # #    # #    # #####  
                                                     
**************************************************************************************************/

abstract class Command {
    protected String helpInfo = "(Not provided)";
    protected String format;
    protected String scene;
    protected List<String> keywords;
    protected ParamList params;
    protected String task;

    Command() {
        ;
    }

    boolean invoked(String word) {
        boolean found = (keywords.contains(word));
        task = found ? word : "";
        return found;
    }

    boolean process(Action thisAction, String in_scene) {
        String line = expandAllOnLine(thisAction.args);
        WordList argv = new WordList(line, tokens);
        scene = in_scene;
        params = new ParamList(argv, format);
        return doProcess();
    }

    String resolveTag(String in_tag) {
        if (in_tag.indexOf(":") >= 0) {
            // already scoped
            return in_tag;
        }
        if (!scene.equals("")) {
            return scene + ":" + in_tag;
        } // else
        return in_tag;
    }

    protected abstract boolean doProcess();

    String help() {
        return (helpInfo);
    }
}


class RandomCommand extends Command {
  
    RandomCommand() {
        keywords = List.of("random", "rand");
        helpInfo = "random variable [from] start [to] [end] (sets varible to random integer)";
        format = "+/name ~/from +/start ~/to ?/end";   
    }

    boolean doProcess() {
        int start = params.asInt("start");
        String name = params.get("name");
        if (params.isEmpty("end")) {
            vars.put(name, String.valueOf((int)random(start)));
        } else {
            int end = params.asInt("end");
            vars.put(name, String.valueOf((int)random(start, end)));
        }
        return false;
    }
}

class UnloadCommand extends Command {
    UnloadCommand() {
        keywords = List.of("unload", "purge");
        helpInfo = "unload tag... (unloads resources, basename as tag if not given)";
        format = "*/rest";
    }

    boolean doProcess() {
        String tagList = params.get("rest");
        for (String tag : splitTokens(tagList, " ")) {
            // look for an image first, using local tag
            String localTag = resolveTag(tag);
            if (images.containsKey(localTag)) {
                images.remove(localTag);
                continue;
            } // else
            if (images.containsKey(tag)) {
                images.remove(tag);
                continue;
            }
            // Next look for sounds
            if (sounds.containsKey(localTag)) {
                sounds.remove(localTag);
                continue;
            } // else
            if (sounds.containsKey(tag)) {
                sounds.remove(tag);
                continue;
            }
            message("No resource found for:", tag);
        }
        return false;
    }
}

class EchoCommand extends Command {

    EchoCommand() {
        keywords = List.of("echo", "say", "print");
        helpInfo = "echo words.... (echoes arguments, including expanded variables)";
        // other arguments later maybe...?
        format = "*/rest";
    }

    boolean doProcess() {
        println(params.get("rest"));
        return true;
    }
}

class PlayCommand extends Command {

    PlayCommand() {
        keywords = List.of("play");
        helpInfo = "play sound";
        // other arguments later maybe...?
        format = "+/tag";
    }

    boolean doProcess() {
        String soundTag = params.get("tag");
        String localTag = resolveTag(soundTag);
        if (sounds.containsKey(localTag)) {
            soundTag = localTag;
        }
        SoundFile sound = sounds.get(soundTag);
        if (sound == null) {
            message("Not an soundfile tag: " + soundTag);
            return true;
        }
        sound.play();
        return true;
    }
}

class LoadCommand extends Command {

    LoadCommand() {
        keywords = List.of("load", "upload");
        helpInfo = "load filename [as] [tag] (loads resource, basename as tag if not given)";
        format = "+/filename ~/as ?/tag";
    }

    boolean doProcess() {
        String filename = params.get("filename");
        String tag = params.get("tag");
        String extension = "";
        int dot = filename.lastIndexOf('.');
        if (dot > 0) {
            extension = filename.substring(dot + 1);
        }
        if (tag.equals("")) {
            tag = filename.substring(0,dot - 1);
        }
        tag = resolveTag(tag);
        switch (extension.toLowerCase()) {
            case "jpg":
            case "jpeg":
            case "png":
            case "gif":
                if (!images.containsKey(tag)) {
                    PImage image = loadImage(filename);
                    images.put(tag, image);
                } // not an error to attempt reload, use purge to force refresh
                break;
            case "wav":
            case "aiff":
            case "mp3":
                if (!sounds.containsKey(tag)) {
                    SoundFile sound = new SoundFile(anim_script.this, filename);
                    sounds.put(tag, sound);
                } // as above
                break;
            default:
                message("Unknown asset format: " + filename);
                break;
        }
        return true;
    }
}

class StartCommand extends Command {

    StartCommand() {
        keywords = List.of("start", "run");
        helpInfo = "start scene (start running a named scene)";
        format = "+/scene";
    }

    boolean doProcess() {
        String sceneName = params.get("scene");
        for (Scene scene : scenes) {
            if (scene.name.equals(sceneName)) {
                scene.start();
            }
        }
        return true;
    }

}

class StopCommand extends Command {

    StopCommand() {
        keywords = List.of("disable", "stop", "finish");
        helpInfo = "stop scene (stop running named scene, or the current scene if not given & removes all sprites from active list)";
        format = "?/scene";
    }

    boolean doProcess() {
        String sceneName = params.get("scene");
        for (Scene scene : scenes) {
            if (scene.name.equals(sceneName)) {
                scene.stop();
            }
        }
        return true;
    }
}


class RemoveCommand extends Command {

    RemoveCommand() {
        keywords = List.of("remove", "delete");
        helpInfo = "remove sprite (deletes from active list)";
        format = "+/tag";
    }

    boolean doProcess() {
        sprites.remove(params.get("tag"));
        return false;
    }
}

class ShowCommand extends Command {

    ShowCommand() {
        keywords = List.of("show", "reveal");
        helpInfo = "show sprites... (reveals sprites in active list)";
        format = "*/rest";
    }

    boolean doProcess() {
        String tagList = params.get("rest");
        for (String tag : splitTokens(tagList, " ")) {
            Sprite sprite = sprites.find(tag, scene);
            if (sprite != null) {
                sprite.show();
            }
        }
        return false;
    }
}

class HideCommand extends Command {

    HideCommand() {
        keywords = List.of("hide", "conceal");
        helpInfo = "hide sprites... (hides sprite, but still active and updating)";
        format = "*/rest";
    }

    boolean doProcess() {
        String tagList = params.get("rest");
        for (String tag : splitTokens(tagList, " ")) {
            Sprite sprite = sprites.find(tag, scene);
            if (sprite != null) {
                sprite.hide();
            }
        }
        return false;
    }
}

class RaiseCommand extends Command {

    RaiseCommand() {
        keywords = List.of("raise", "lift", "lower", "shift", "drop");
        helpInfo = "raise/lower tag [to/by depth]";
        format = "+/tag |/to|by ?/depth";
    }

    boolean doProcess() {
        String tag = params.get("tag");
        String method = params.get("to");
        int depth = params.asInt("depth", -1);
        int direction = (task.equals("lower") || task.equals("drop")) ? -1 : 1;
        Sprite sprite = sprites.find(tag, scene);
        if (sprite == null) {
            return true;
        }
        if (depth < 0) { 
            sprites.moveBy(sprite, 1 * direction);
            // message("Raise", tag, "by 1");
        } else {
            if (method.equals("to")) {
                sprites.setZ(sprite, depth * direction);
//                message("Raise to layer", params.get("depth"));
            } else if (method.equals("by")) {
                sprites.moveBy(sprite, depth * direction);
                // message("Raise by", params.get("depth"), "layers");
            }
        }
        return true;
    }
}

class PlaceCommand extends Command {
    
    PlaceCommand() {
        keywords = List.of("place", "put");
        helpInfo = "place image [as sprite] [at] x,y,z [size w,h] (adds to active list but DOES not show sprite)";
        format = "+/itag ~/as &/stag ~/at +/x +/y ~/depth +/z ~/size ?/w ?/h";
    }

    boolean doProcess() {
        String itag = params.get("itag");
        String stag = params.get("stag");
        if (stag.equals("")) {
            stag = itag;
        }
        String localtag = resolveTag(itag);
        if (images.containsKey(localtag)) {
            itag = localtag;
        }
        if (images.get(itag) == null) { 
            message("Not an image tag: " + itag);
            return true;
        } // else
        stag = resolveTag(stag);
        float x = params.asFloat("x");
        float y = params.asFloat("y");
        int z = params.asInt("z");
        float w = params.asFloat("w");
        float h = params.asFloat("h");
        if (w <= 0.0) { // not given size
            sprites.add(new Sprite(itag, stag, x, y, z, scene));
        } else {
            sprites.add(new Sprite(itag, stag, x, y, z, w, h, scene));
        }
        return false;
    }
}

class MoveCommand extends Command {
    
    MoveCommand() {
        keywords = List.of("move");
        helpInfo = "move tag [to] x,y [in] [time] (moves sprite to new position, whether hidden or visible)";
        format = "+/tag ~/to +/x +/y ~/in ?/time";
    }

    boolean doProcess() {
        Sprite sprite = sprites.find(params.get("tag"), scene);
        if (sprite != null) {
            float x = params.asFloat("x");
            float y = params.asFloat("y");
            float elapsed = params.asFloat("time");
            sprite.move(x, y, elapsed);
        }
        return false;
    }
}

class FadeCommand extends Command {
    
    FadeCommand() {
        keywords = List.of("fade", "trans");
        helpInfo = "fade tag to value [in time]";
        format = "+/tag ~/to +/trans ~/in ?/time";
    }

    boolean doProcess() {
        Sprite sprite = sprites.find(params.get("tag"), scene);
        if (sprite != null) {
            float trans = params.asFloat("trans");
            float elapsed = params.asFloat("time");
            sprite.fade(trans, elapsed);
        }
        return false;
    }
}

class RotateCommand extends Command {
    
    RotateCommand() {
        keywords = List.of("rotate", "turn");
        helpInfo = "rotate tag [to] r [in] [time] (rotates sprite to new angle, whether hidden or visible)";
        format = "+/tag ~/to +/r ~/in ?/time";
    }

    boolean doProcess() {
        Sprite sprite = sprites.find(params.get("tag"), scene);
        if (sprite != null) {
            float r = params.asFloat("r");
            float elapsed = params.asFloat("time");
            sprite.turn(r, elapsed);
        }
        return false;
    }
}

class ResizeCommand extends Command {
    
    ResizeCommand() {
        keywords = List.of("size", "resize");
        helpInfo = "resize tag [to] x,y [in] [time] (resizes sprite to new size, whether hidden or visible)";
        format = "+/tag ~/to +/x +/y ~/in ?/time";
    }

    boolean doProcess() {
        Sprite sprite = sprites.find(params.get("tag"), scene);
        if (sprite != null) {
            float x = params.asFloat("x");
            float y = params.asFloat("y");
            float elapsed = params.asFloat("time");
            sprite.resize(x, y, elapsed);
        }
        return false;
    }

}

class ReadCommand extends Command {
    
    ReadCommand() {
        keywords = List.of("read");
        helpInfo = "read filename (add to list of actions from file)";
        format = "+/filename";
    }

    boolean doProcess() {
        readFile(params.get("filename"));
        return true;
    }

}

class SetCommand extends Command {
    
    SetCommand() {
        keywords = List.of("set");
        helpInfo = "set name [as] value (can be accessed as $name in other commands)";
        format = "+/name ~/to */rest";
    }

    boolean doProcess() {  // set name as value
        String name = params.get("name");
        String content = params.get("rest");
        // check for built-ins first
        switch(name) {
            case "FRAMERATE":
                int fr = Integer.parseInt(content);
                if (fr > 0) {
                    FRAMERATE = fr;
                    frameRate(FRAMERATE);
                }
                break;
            default:
                vars.put(name, content);
                break;
        }
        return false;
    }

}

class CalcCommand extends Command {

    CalcCommand() {
        keywords = List.of("calc", "calculate", "eval", "evaluate");
        format = "+/name ~/as */rest";
        helpInfo = "calculate name [as] expression (can be accessed as $name in other actions)";
    }

    boolean doProcess() {  // set name as value
        String expression = params.get("rest");
        double result = eval(expression);
        vars.put(params.get("name"),  String.valueOf(result));
        return false;
    }
}

class ExitCommand extends Command {

    ExitCommand() {
        keywords = List.of("exit", "endprogram");
        format = "";
        helpInfo = "End program. (Exits processing)";
    }

    boolean doProcess() {  
        System.exit(0);
        return false;
    }
}

class DebugCommand extends Command {

    DebugCommand() {
        keywords = List.of("debug", "dump");
        format = "+/what";
        helpInfo = "debug vars|sprites|commands|actions|triggers";
    }

    boolean doProcess() {
        switch (params.get("what")) {
            case "commands":
                commandProcessor.printHelp();
                break;
            case "sprites":
                sprites.dumpSprites();
                break;
            case "actions":
                for (Action action : actions) {
                    action.dumpAction();
                }
                break;
            case "vars":
                for (Map.Entry me : vars.entrySet()) {
                    print(me.getKey() + " => ");
                    println(me.getValue());
                }
                break;
        }
        return true;
    }
}

/*************************************************************************************************

   #     #                      
   #     #   ##   #####   ####  
   #     #  #  #  #    # #      
   #     # #    # #    #  ####  
    #   #  ###### #####       # 
     # #   #    # #   #  #    # 
      #    #    # #    #  ####  
                                
**************************************************************************************************/

String expandVar(String word) {
    // built in variables
    String value = "";
    switch(word) {
        case "SECOND": 
            value = String.valueOf(second());
            break;
        case "MINUTE":
            value = String.valueOf(minute());
            break;
        case "HOUR":
            value = String.valueOf(hour());
            break;
        case "FRAMERATE":
            value = String.valueOf(FRAMERATE);
            break;
        case "WIDTH":
            value = String.valueOf(width);
            break;
        case "HEIGHT":
            value = String.valueOf(height);
            break;
        case "CENTERX":
        case "CENTREX":
            value = String.valueOf(width / 2);
            break;
        case "CENTERY":
        case "CENTREY":
            value = String.valueOf(height / 2);
            break;
        case "PERCENT":
            value = String.valueOf((int)random(0,100));
            break;
        case "RANDOMX":
            value = String.valueOf((int)random(0,width));
            break;
        case "RANDOMY":
            value = String.valueOf((int)random(0,height));
            break;        // scene name? times of day?
    }
    if (value.equals("") && vars.containsKey(word)) {
        return vars.get(word);
    }
    if (value.equals("")) {
        message("variable not found", word);
    }
    return value;
}

String expandAllOnLine(String line) {
    if (line == null || line.length() < 1) {
        return "";
    }
    StringBuilder varName = new StringBuilder();
    StringBuilder newLine = new StringBuilder(line.length());
    int pos = 0;
    String whiteSpace = " ,.\t;";

    boolean readingName = false;
    boolean inBraces = false;
    boolean changed = false;
    for(pos = 0; pos < line.length(); pos++) {
        char c = line.charAt(pos);
        if (c == '$') {
            varName = new StringBuilder();
            changed = true;
            readingName = true;
            continue;
        }
        if (readingName && c == '{') {
            inBraces = true;
            continue;
        }
        if (inBraces && c == '}') {
            newLine.append(expandVar(varName.toString()));
            varName = new StringBuilder();
            inBraces = false;
            readingName = false;
            continue;
        }
        if (readingName && whiteSpace.indexOf(c) >= 0) {
            newLine.append(expandVar(varName.toString()));
            newLine.append(' ');
            varName = new StringBuilder();
            readingName = false;
            continue;
        }
        if (readingName) {
            varName.append(c);
        } else {
            newLine.append(c);
        }
    }
    if (varName.length() > 0) {
        newLine.append(expandVar(varName.toString()));
    }
    if (changed) {
        // message("expanded: ", line, " to ", newLine.toString());
        return newLine.toString();
    } // else
    return line;
}

void setVar(String name, String value) {
    vars.put(name, value);
}

/*************************************************************************************************

    #####                                            ######                
   #     #  ####  #    # #    #   ##   #    # #####  #     # #####   ####  
   #       #    # ##  ## ##  ##  #  #  ##   # #    # #     # #    # #    # 
   #       #    # # ## # # ## # #    # # #  # #    # ######  #    # #    # 
   #       #    # #    # #    # ###### #  # # #    # #       #####  #    # 
   #     # #    # #    # #    # #    # #   ## #    # #       #   #  #    # 
    #####   ####  #    # #    # #    # #    # #####  #       #    #  ####  
                                                                           
                                          
 ####  ######  ####   ####   ####  #####  
#    # #      #      #      #    # #    # 
#      #####   ####   ####  #    # #    # 
#      #           #      # #    # #####  
#    # #      #    # #    # #    # #   #  
 ####  ######  ####   ####   ####  #    # 
                                          
**************************************************************************************************/

class CommandProcessor {
    ArrayList<Command> commandList = new ArrayList<Command>();

    CommandProcessor() {
        // Could possibly replace with some reflection and instantiating all the
        // subclasses of command but if we do it manually we can put the most
        // common commands first in the list
        commandList.add(new SetCommand());
        commandList.add(new PlaceCommand());
        commandList.add(new ShowCommand());
        commandList.add(new HideCommand());
        commandList.add(new MoveCommand());
        commandList.add(new ResizeCommand());
        commandList.add(new RemoveCommand());
        commandList.add(new RotateCommand());
        commandList.add(new RaiseCommand());
        commandList.add(new PlayCommand());
        commandList.add(new FadeCommand());
        commandList.add(new HideCommand());
        commandList.add(new StopCommand());
        commandList.add(new StartCommand());
        commandList.add(new LoadCommand());
        commandList.add(new RandomCommand());
        commandList.add(new CalcCommand());
        commandList.add(new ReadCommand());
        commandList.add(new EchoCommand());
        commandList.add(new ExitCommand());
        commandList.add(new UnloadCommand());
        commandList.add(new DebugCommand());
    }

    void dispatch(Action thisAction, String scene) {
        boolean complete = false;  // make sure not to execute twice
        boolean foundCommand = false;
        for(Command command : commandList) {
            if (command.invoked(thisAction.task)) {
                complete = command.process(thisAction, scene);
                foundCommand = true;
                break;
            }
        }
        if (!foundCommand) {
            message("Unknown command: " + thisAction.task);
        }
        // also mark complete if trigger was after or attime (only done once)
        String triggerType = thisAction.trigger.getClass().getSimpleName();
        if (triggerType.equals("After") || triggerType.equals("AtTime") || triggerType.equals("Start")) {
            complete = true;
        }
        if (complete) {
            thisAction.complete = true;
        }
    }

    void printHelp() {
        for(Command command : commandList) {
            println(command.help());
        }
    }
}
/*************************************************************************************************

    #####                              
   #     #  ####  ###### #    # ###### 
   #       #    # #      ##   # #      
    #####  #      #####  # #  # #####  
         # #      #      #  # # #      
   #     # #    # #      #   ## #      
    #####   ####  ###### #    # ###### 
                                       
**************************************************************************************************/

class Scene {
    ArrayList<Action> sceneActions = new ArrayList<Action>();

    String name;
    String filename;
    boolean enabled = false;
    String[] content;

    Scene(String in_name, String[] in_content, String in_filename) {
        name = in_name;
        content = in_content;
        filename = in_filename;
        if (name.equals("")) {
            enabled = true;
        }
    }

    void start() {
        enabled = true;
        Trigger trigger = new Start();
        for (String line : content) {
            WordList words = new WordList(line, ", \t");
            String first = words.get(0).toLowerCase();
            switch(first) {
                case "begin":
                    trigger = new Start();
                    break;
                case "after":
                    trigger = new After(words.from(1));
                    break;
                case "every":
                    trigger = new Every(words.get(1));
                    break;
                case "at":
                    trigger = new AtTime(words.get(1));
                    break;
                case "onkey":
                    trigger = new OnKey(words.get(1));
                    break;
                default: // must be an action of some sort
                    Action action = new Action(words);
                    action.setTrigger(trigger);
                    action.complete = false;
                    sceneActions.add(action);
                    break;
            }
        } 
        for (Action action : sceneActions) {
            action.trigger.reset();
            if (action.triggered()) {
                commandProcessor.dispatch(action, name);
            }
        }
    }

    void stop() {
        if (name.equals("")) {
            // Must be top level, never stop this one!
            return;
        }
        enabled = false;
        sprites.stopScene(name);
        return;
    }

    void end() {
        enabled = false;
        sceneActions = new ArrayList<Action>();
    }

    void dump() {
        String state = enabled ? "Enabled" : "Disabled";
        String displayName = name == null ? "(top)" : name;
        println("Scene: " + displayName + " (from " + filename + ") - " + state);
        for(String line : content) {
            println(line);
        }
    }

    void dumpActions() {
        for (Action action : actions) {
            action.dumpAction();
        }
    }
}

ArrayList<Scene> scenes = new ArrayList<Scene>();

/*************************************************************************************************

                                #####                               
   #####  ######   ##   #####  #     #  ####  #####  # #####  ##### 
   #    # #       #  #  #    # #       #    # #    # # #    #   #   
   #    # #####  #    # #    #  #####  #      #    # # #    #   #   
   #####  #      ###### #    #       # #      #####  # #####    #   
   #   #  #      #    # #    # #     # #    # #   #  # #        #   
   #    # ###### #    # #####   #####   ####  #    # # #        #   
                                                                    
**************************************************************************************************/

void readFile(String filename) {
    int lineCount = 0;
    String currentScene = "";
    ArrayList<String> holding = new ArrayList<String>();
    ArrayList<String> topLevel = new ArrayList<String>();
    String tokens = " ,\t;.";
    
    String[] lines = loadStrings(filename);

    boolean in_comment = false;
    for ( String line : lines) {
        lineCount += 1;
        line = trim(line);
        // ignore comments and blank lines
        if (line.length() < 2 || line.charAt(0) == '#') {
            continue;
        }
        if (line.charAt(0) == '/' && line.charAt(1) == '*') {
            in_comment = true;
            continue;
        }
        if (in_comment) {
            int endOfLine = line.length();
            if (line.charAt(endOfLine - 2) == '*' && line.charAt(endOfLine - 1) == '/') {
                in_comment = false;
            }
            continue;
        }
        // look for scene markers
        if (line.toLowerCase().startsWith("scene")) {
            String[] words = splitTokens(line, tokens);
            if (words.length < 2) {
                message("expected scene name", filename, String.valueOf(lineCount));
            } else {
                currentScene = words[1];
                if (holding.size() > 0) {
                    scenes.add(new Scene(currentScene, holding.toArray(new String[0]), filename));
                    holding = new ArrayList<String>();
                }
            }
        } else if (line.toLowerCase().startsWith("endscene")) {
            if (holding.size() > 0) {
                scenes.add(new Scene(currentScene, holding.toArray(new String[0]), filename));
                holding = new ArrayList<String>();
                currentScene = "";
            }
        } else if (line.toLowerCase().startsWith("include")) {
            String[] words = splitTokens(line, tokens);
            if (words.length < 2) {
                message("expected filename", filename, String.valueOf(lineCount));
            } else {
                for (int i = 0; i < words.length; i++) {
                    readFile(words[i]);
                }
            }
        } else if (line.toLowerCase().startsWith("finish")) {
            // stop processing this file
            break;
        } else { // just add the line to the current scene
            if (currentScene.equals("")) {
                topLevel.add(line);
            } else {
                holding.add(line);
            }
        }
    }
    // finished reading file
    if (topLevel.size() > 0) {
        scenes.add(new Scene("", topLevel.toArray(new String[0]), filename));
    }
    if (holding.size() > 0) {
        scenes.add(new Scene(currentScene, holding.toArray(new String[0]), filename));
    }
}

void readScript() {
    if (args != null) {
        for (int i = 0; i < args.length; i++) {
            readFile(args[i]);
        }
    } else {
        readFile(scriptFile);
    }
    // Start running top level scene
    for(Scene scene : scenes) {
        if (scene.name.equals("")) {
            scene.start();
        }
    }
}

/*************************************************************************************************

   #     #                    #                            
   ##   ##   ##   # #    #    #        ####   ####  #####  
   # # # #  #  #  # ##   #    #       #    # #    # #    # 
   #  #  # #    # # # #  #    #       #    # #    # #    # 
   #     # ###### # #  # #    #       #    # #    # #####  
   #     # #    # # #   ##    #       #    # #    # #      
   #     # #    # # #    #    #######  ####   ####  #      
                                                           
**************************************************************************************************/

void doActions() {
    for (Scene scene : scenes) {
        if (scene.enabled) {
            for (int i = 0; i < scene.sceneActions.size(); i++) {
                Action thisAction = scene.sceneActions.get(i);
                if (!thisAction.complete && thisAction.triggered()) {
                    commandProcessor.dispatch(thisAction, scene.name);
                }
            }
        }
    }
}

void setup() {
    size(1920,1080);
    commandProcessor = new CommandProcessor();
    readScript();
    colorMode(100,100);
    imageMode(CENTER);
    frameRate(FRAMERATE);

};

void draw() {
    // Actions have a resolution of 1 second, so only do them if the second changes
    if (thisSecond != second()) {
        thisSecond = second();
        doActions();
    }
    // but update sprites on every frame
    drawSprites();
};