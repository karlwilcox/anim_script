import processing.core.*;

/* +++START PDE+++ */
import java.util.Map;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.List;
import processing.sound.*;

import java.util.HashMap;
import java.util.ArrayList;

/* +++END PDE+++ */

public class anim_script extends PApplet {


    /* +++START PDE+++ */
    final boolean DEBUG = true;

/* To run on the command line:
processing-java --sketch=~/Documents/Processing/lego_animation --force --output=~/Documents/Processing/lego_animation/output --run
*/

    int FRAMERATE = 10;
    static int SIZE_X = 1080;
    static int SIZE_Y = 1920;

    HashMap<String, SpriteImage> images = new HashMap<String, SpriteImage>();
    HashMap<String, SoundFile> sounds = new HashMap<String, SoundFile>();
    ArrayList<Action> actions = new ArrayList<Action>();
    SpriteList sprites = new SpriteList();
    HashMap<String, String> vars = new HashMap<String, String>();
    CommandProcessor commandProcessor;

    int thisSecond = 0;
    String scriptFile = "script.txt";
    final String tokens = " \t,;";

    public void message(String... messages) {
        if (DEBUG) {
            for (String message : messages) {
                print(message + " ");
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
    public double eval(final String str) {
        return new Object() {
            int pos = -1, ch;

            public void nextChar() {
                ch = (++pos < str.length()) ? str.charAt(pos) : -1;
            }

            public boolean eat(int charToEat) {
                while (ch == ' ') nextChar();
                if (ch == charToEat) {
                    nextChar();
                    return true;
                }
                return false;
            }

            public double parse() {
                nextChar();
                double x = parseExpression();
                if (pos < str.length()) throw new RuntimeException("Unexpected: " + (char) ch);
                return x;
            }

            // Grammar:
            // expression = term | expression `+` term | expression `-` term
            // term = factor | term `*` factor | term `/` factor
            // factor = `+` factor | `-` factor | `(` expression `)` | number
            //        | functionName `(` expression `)` | functionName factor
            //        | factor `^` factor

            public double parseExpression() {
                double x = parseTerm();
                for (; ; ) {
                    if (eat('+')) x += parseTerm(); // addition
                    else if (eat('-')) x -= parseTerm(); // subtraction
                    else return x;
                }
            }

            public double parseTerm() {
                double x = parseFactor();
                for (; ; ) {
                    if (eat('*')) x *= parseFactor(); // multiplication
                    else if (eat('/')) x /= parseFactor(); // division
                    else if (eat('>')) x = x > parseFactor() ? 1.0f : 0.0f; // greater than
                    else if (eat('<')) x = x < parseFactor() ? 1.0f : 0.0f; // less than
                    else if (eat('=')) x = x == parseFactor() ? 1.0f : 0.0f; // equals - use with care
                    else return x;
                }
            }

            public double parseFactor() {
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
                    switch (func) {
                        case "sqrt":
                            x = Math.sqrt(x);
                            break;
                        case "sin":
                            x = Math.sin(Math.toRadians(x));
                            break;
                        case "cos":
                            x = Math.cos(Math.toRadians(x));
                            break;
                        case "tan":
                            x = Math.tan(Math.toRadians(x));
                            break;
                        default:
                            throw new RuntimeException("Unknown function: " + func);
                    }
                } else {
                    throw new RuntimeException("Unexpected: " + (char) ch);
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

        public void update() {
            int elapsed = millis() - startTime;
            // message(String.valueOf(elapsed));
            hour = elapsed / (1000 * 60 * 60);
            elapsed -= hour * 1000 * 60 * 60;
            minute = elapsed / (1000 * 60);
            elapsed -= minute * 1000 * 60;
            second = elapsed / 1000;
        }

        public void reset() {
            startTime = millis();
        }
    }

    class Now extends Timer {
        Now() {
            ;
        }

        public void update() {
            hour = hour();
            minute = minute();
            second = second();
        }

        public void reset() {
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

        public void setFromDuration(List<String> words, String notGiven) {
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
                        switch (words.get(wordPos).toLowerCase()) {
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
            } while (!value.isEmpty() && wordPos < words.size());
            hour = new Match(h);
            minute = new Match(m);
            second = new Match(s);
        }


        TimeCode(String timecode) {
            this(timecode, "*");
        }

        TimeCode(List<String> words, String notGiven) {
            if (words.get(0).matches(".*:.*")) {
                setFromCode(words.get(0), notGiven);
                return;
            } // else
            setFromDuration(words, notGiven);
        }

        TimeCode(String timecode, String notGiven) {
            setFromCode(timecode, notGiven);
        }

        public void setFromCode(String timecode, String notGiven) {
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

        public boolean matches(Timer timer) {
            timer.update();
            return (hour.matches(timer.hour) && minute.matches(timer.minute) && second.matches(timer.second));
        }

        public boolean after(Timer timer) {
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
                    value = PApplet.parseInt(pattern.substring(1));
                    type = PERCENT;
                    break;
                default:
                    value = PApplet.parseInt(pattern);
                    type = TIME;
                    break;
            }
        }

        public boolean matches() {
            return matches(-1);
        }

        public boolean after(int time) {
            // only really makes sense for actual times
            if (type == TIME) {
                return (time >= value);
            } // else
            return true;
        }

        public boolean matches(int time) {
            boolean match = false;
            switch (type) {
                case ALWAYS:
                    match = true;
                    break;
                case NEVER:
                    break;
                case PERCENT:
                    match = (PApplet.parseInt(random(100)) > (100 - value));
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
            if (words.size() < 1 || words.get(argPos).isEmpty()) {
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
                            cond.append(endif).append(" ");
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

        public boolean triggered() {
            return (trigger.triggered() && conditionTrue());
        }

        public boolean conditionTrue() {
            boolean outcome = true;
            boolean expressionFound = true;
            String cond = expandAllOnLine(condition);

            if (cond.isEmpty()) {
                return true;
            } // else
            // Try to evaluate it as an expression
            try {
                outcome = (eval(cond) != 0.0f);
            } catch (RuntimeException e) {
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
                    switch (comparison) {
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
                    outcome = lhs.equalsIgnoreCase("true");
                }
            }
            if (!outcome) {
                // for some triggers, we don't test again after the first try
                String triggerType = trigger.getClass().getSimpleName();
                if (triggerType.equals("After") || triggerType.equals("AtTime") || triggerType.equals("Start")) {
                    complete = true;
                }
            }
            return outcome;
        }

        public void setTrigger(Trigger in_trigger) {
            trigger = in_trigger;
        }

        public void dumpAction() {
            String status = complete ? "(completed)" : "available";
            println(task + ": " + String.join(" ", args) + status
                    + " Trigger: " + trigger.getClass().getSimpleName());
        }

    }

/*************************************************************************************************

    #####                               ###
   #     # #####  #####  # ##### ######  #  #    #   ##    ####  ######
   #       #    # #    # #   #   #       #  ##  ##  #  #  #    # #
    #####  #    # #    # #   #   #####   #  # ## # #    # #      #####
         # #####  #####  #   #   #       #  #    # ###### #  ### #
   #     # #      #   #  #   #   #       #  #    # #    # #    # #
    #####  #      #    # #   #   ###### ### #    # #    #  ####  ######

**************************************************************************************************/

    class SpriteImage {
        ArrayList<PImage> spriteFrames = new ArrayList<PImage>();
        int numFrames = 0;
        int frameWidth;
        int frameHeight;

        SpriteImage(String filename) {
            this(filename, 1, 1);
        }

        SpriteImage(String filename, int columns, int rows) {
            PImage source = loadImage(filename);
            frameWidth = source.width / columns;
            frameHeight = source.height / rows;
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < columns; c++) {
                    int x = c * frameWidth;
                    int y = r * frameHeight;
                    spriteFrames.add(source.get(x, y, frameWidth, frameHeight));
                }
            }
            numFrames = rows * columns;
        }

        PImage getSpriteFrame(int i) {
            if (i < numFrames) {
                return spriteFrames.get(i);
            } // else
            return null;
        }

        int size() {
            return numFrames;
        }

        int width() {
            return frameWidth;
        }

        int height() {
            return frameHeight;
        }
    }

/*************************************************************************************************

    #####
   #     # #####  #####  # ##### ######
   #       #    # #    # #   #   #
    #####  #    # #    # #   #   #####
         # #####  #####  #   #   #
   #     # #      #   #  #   #   #
    #####  #      #    # #   #   ######

**************************************************************************************************/


    class Sprite {

        class Adjustable {
            private float currentValue;
            private float targetValue;
            private float deltaValue;
            private final float upperLimit;
            private final float lowerLimit;

            Adjustable() {
                this(0.0f);
            }

            Adjustable(float in_value) {
                this(in_value, MIN_FLOAT, MAX_FLOAT);
            }

            Adjustable(float in_value, float in_lower, float in_upper) {
                currentValue = in_value;
                targetValue = in_value;
                deltaValue = 0.0f;
                lowerLimit = in_lower;
                upperLimit = in_upper;
                parameters.add(this);
            }

            float value() {
                return currentValue;
            }

            void setTargetValue(float in_value, float in_seconds) {
                if (targetValue < lowerLimit) {
                    targetValue = lowerLimit;
                } else if (targetValue > upperLimit) {
                    targetValue = upperLimit;
                }
                targetValue = in_value;
                if (in_seconds < 0.001f) { // move now
                    currentValue = in_value;
                    deltaValue = 0.0f;
                } else {
                    deltaValue = (targetValue - currentValue) / (in_seconds * FRAMERATE);
                }
            }

            void updateValue() {
                if (Math.abs(currentValue - targetValue) > Math.abs(deltaValue)) {
                    currentValue += deltaValue;
                }
            }
        }

        ArrayList<Adjustable> parameters = new ArrayList<Adjustable>();

        String imageTag;
        String tag;
        String scene;
        private boolean visible = false;
        // current values
        Adjustable x = new Adjustable();
        Adjustable y = new Adjustable();
        Adjustable w = new Adjustable();
        Adjustable h = new Adjustable();
        Adjustable r = new Adjustable();
        Adjustable alpha = new Adjustable(0, 0, 100);
        Adjustable gray = new Adjustable(100,0,100);
        Adjustable framesPerSpriteFrame = new Adjustable(1.0f);
        int z = 0;
        int rx = 0;    // centre of rotation
        int ry = 0;


        private int currentSpriteFrame = 0;
        private int lastFrameCount = 0;

        public Sprite(String in_imageTag, String in_tag, String in_scene) {
            this(in_imageTag, in_tag, 0.0f, 0.0f, 0, in_scene);
        }

        public Sprite(String in_imageTag, String in_tag, float in_x, float in_y, int in_z, String in_scene) {
            this(in_imageTag, in_tag,
                    in_x, in_y, in_z, -1.0f, -1.0f,
                    in_scene);
        }

        public Sprite(String in_imageTag, String in_tag,
                      float in_x, float in_y, int in_z, float in_w, float in_h,
                      String in_scene) {
            scene = in_scene;
            x.setTargetValue(in_x, 0.0f);
            y.setTargetValue(in_y, 0.0f);
            z = in_z;
            if (in_w < 0.0f) {
                SpriteImage spriteImage = images.get(in_imageTag);
                in_w = spriteImage.width();
                in_h = spriteImage.height();
            }
            w.setTargetValue(in_w, 0.0f);
            h.setTargetValue(in_h, 0.0f);
            imageTag = in_imageTag;
            tag = in_tag;
        }

        public void setGray(float to_trans, float in_seconds) {
            alpha.setTargetValue(to_trans, in_seconds);
        }

        public void setTint(float to_trans, float in_seconds) {
            gray.setTargetValue(to_trans, in_seconds);
        }

        public void move(float to_x, float to_y, float in_seconds) {
            x.setTargetValue(to_x, in_seconds);
            y.setTargetValue(to_y, in_seconds);
        }

        public void resize(float to_w, float to_h, float in_seconds) {
            w.setTargetValue(to_w, in_seconds);
            h.setTargetValue(to_h, in_seconds);
        }

        public void turn(float to_r, float in_seconds) {
            r.setTargetValue(to_r, in_seconds);
        }

        public void setCenter(int in_rx, int in_ry) {
            rx = in_rx;
            ry = in_ry;
        }

        public void show() {
            visible = true;
        }

        public void hide() {
            visible = false;
        }

        public boolean isVisible() {
            return visible;
        }

        public void update() {
            for (Adjustable parameter : parameters) {
                parameter.updateValue();
            }
        }

        public void updateRate(float updatesPerSecond, float in_seconds) {
            framesPerSpriteFrame.setTargetValue(FRAMERATE * updatesPerSecond, in_seconds);
        }

        public void display() {
            SpriteImage spriteImage = images.get(imageTag);
            if ((frameCount - lastFrameCount) >= (int)framesPerSpriteFrame.value()) {
                currentSpriteFrame = (currentSpriteFrame + 1) % spriteImage.size();
                lastFrameCount = frameCount;
            }
            PImage image = spriteImage.getSpriteFrame(currentSpriteFrame);
            if (alpha.value() > 0) {
                tint(100, (int) alpha.value());
            }
            if (gray.value() < 100) {
                // message("gray value", String.valueOf(gray.value()));
                tint(gray.value());
            }
            if (r.value() != 0.0f) {
                pushMatrix();
                translate(x.value() + (float)rx, y.value() + (float)ry);
                rotate(radians(r.value()));
                image(image, 0, 0, w.value(), h.value());
                popMatrix();
            } else { // more simple
                image(image, x.value(), y.value(), w.value(), h.value());
            }
            noTint();
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

        public ListIterator<Sprite> each() {
            return spriteList.listIterator(0);
        }

        public void add(Sprite sprite) {
            int i;
            for (i = 0; i < spriteList.size(); i++) {
                if (sprite.z > spriteList.get(i).z) {
                    break;
                }
            }
            spriteList.add(i, sprite);
        }

        public void moveTo(int from, int to) {
            if (from < 0 || to < 0 || from == to) {
                return;
            } // else
            Sprite sprite = spriteList.get(from);
            // need to find proper z value, make it same as previous
            sprite.z = (to > 0) ? spriteList.get(to - 1).z : spriteList.get(0).z;
            spriteList.add(to, sprite);
            spriteList.remove(from);
        }

        public void setZ(Sprite sprite, int in_z) {
            for (int i = 0; i < spriteList.size(); i++) {
                if (spriteList.get(i) == sprite) {
                    sprite.z = in_z;
                    spriteList.remove(i);
                    add(sprite);
                    return;
                }
            }
        }

        public void moveBy(Sprite sprite, int count) {
            int moveTo = -1;
            int moveFrom = -1;
            int size = spriteList.size();
            for (int i = 0; i < size; i++) {
                if (spriteList.get(i) == sprite) {
                    // we have our sprite
                    moveFrom = i;
                    // don't overflow
                    if (count < 0) {
                        moveTo = Math.max((i + count), 0);
                    } else {
                        moveTo = (size < i + count) ? i + count : size - 1;
                    }
                    moveTo(moveFrom, moveTo);
                    return;
                }
            }
        }

        public void remove(String in_tag) {
            for (int i = 0; i < spriteList.size(); i++) {
                if (in_tag.equals(spriteList.get(i).tag)) {
                    spriteList.remove(i);
                    break;
                }
            }
        }

        public Sprite find(String in_tag, String in_scene) {
            // look for a local tag first
            if ((!in_tag.contains(":")) && !in_scene.isEmpty()) {
                String localTag = in_scene + ":" + in_tag;
                for (Sprite sprite1 : spriteList) {
                    if (localTag.equals(sprite1.tag)) {
                        return sprite1;
                    }
                }
            } // not an error if not found, look globally
            for (Sprite sprite2 : spriteList) {
                if (in_tag.equals(sprite2.tag)) {
                    return sprite2;
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
                if (!sprite.scene.isEmpty()) {
                    inScene = " in scene " + sprite.scene;
                }
                message(sprite.tag + " => " + sprite.imageTag + " in scene " + sprite.scene + visibility,
                        String.valueOf(sprite.z));
            }
        }

        public void stopScene(String in_scene) {
            Sprite sprite;
            if (in_scene.isEmpty()) {
                return;
            }
            for (int i = spriteList.size() - 1; i >= 0; i--) {
                sprite = spriteList.get(i);
                if (sprite.scene.equals(in_scene)) {
                    spriteList.remove(i);
                }
            }
        }
    }

    public void drawSprites() {
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

        public String get(int i) {
            if (i < words.size()) {
                return words.get(i);
            } // else
            return defaultValue;
        }

        public void put(int i, String content) {
            if (i < words.size()) {
                words.set(i, content);
            }
        }

        public ArrayList<String> from(int i) {
            if (i < words.size()) {
                return new ArrayList<String>(words.subList(i, words.size()));
            } // else
            return new ArrayList<String>();
        }

        public String getRestAsStr(int from) {
            String rest = "";
            for (int i = from; i < words.size(); i++) {
                rest = rest + words.get(i);
                if (i < words.size() - 1) {
                    rest = rest + " ";
                }
            }
            return rest;
        }

        public int size() {
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

        public boolean triggered() {
            return false;
        }

        public void reset() {
            if (timer != null) {
                timer.reset();
            }
        }
    }

    class Start extends Trigger {

        public boolean triggered() {
            return true;
        }
    }

    class OnKey extends Trigger {
        char triggerKey;

        OnKey(String word) {
            triggerKey = word.charAt(0);
        }

        public boolean triggered() {
            return (keyPressed && (triggerKey == key));
        }
    }

    class After extends Trigger {

        After(List words) {
            timer = new Timer();
            timecode = new TimeCode(words, "0");
        }

        public boolean triggered() {
            return timecode.after(timer);
        }
    }

    class Every extends Trigger {
        Every(String word) {
            timer = new Timer();
            timecode = new TimeCode(word, "*");
        }

        public boolean triggered() {
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

            String[] paramList = splitTokens(format, " ");
            int argPos = 0;
            boolean matchFound = false;
            for (String paramItem : paramList) {
                String[] parts = splitTokens(paramItem, "/");
                if (parts.length != 2) {
                    message("Bad parameter format: " + paramItem);
                    continue;
                }
                char optionality = parts[0].charAt(0);
                String name = parts[1];
                String[] choices = null;
                if (optionality == GETREST) {
                    params.put(name, words.getRestAsStr(argPos));
                    return;
                }
                if (optionality == IFMATCHED && !matchFound) {
                    // only look for this if the previous CANMATCH was found
                    params.put(name, "");
                    continue;
                }
                if (optionality == CHOICE) {
                    choices = splitTokens(name, "|");
                    name = choices[0];
                }
                int step = 1;
                if (argPos < words.size()) { // argument is present
                    String arg = words.get(argPos);
                    switch (optionality) {
                        case OPTIONAL:
                        case REQUIRED:
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
                            for (String choice : choices) {
                                if (arg.equals(choice)) {
                                    params.put(name, choice);
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
                    switch (optionality) {
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
            params.put(key, value);
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

        public boolean isEmpty(String key) {
            return (params.containsKey(key) && params.get(key).isEmpty());
        }

        public int asInt(String key) {
            return asInt(key, 0);
        }

        public int asInt(String key, int notGiven) {
            String string = get(key);
            if (string.isEmpty()) {
                return notGiven; // not a error, just not provided
            }
            if (!string.matches("\\d+")) {
                message("Expected integer: " + string);
                return notGiven;
            } // else
            return (Integer.parseInt(string));
        }

        public float asFloat(String key) {
            String string = get(key);
            if (string.isEmpty()) {
                return 0.0f; // not a error, just not provided
            }
            if (!(string.matches("-?\\d+(\\.\\d+)?"))) {
                message("Expected integer: " + string);
                return 0.0f;
            } // else
            return Float.parseFloat(string);
        }

        public void dumpParams() {
            for (Map.Entry me : params.entrySet()) {
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

        public boolean invoked(String word) {
            boolean found = (keywords.contains(word));
            task = found ? word : "";
            return found;
        }

        private String evaluateBrackets(String line) {
            boolean changed = false;
            boolean inExpression = false;
            int openCount = 0;

            if (line == null || line.isEmpty()) {
                return "";
            }
            StringBuilder expression = new StringBuilder();
            StringBuilder newLine = new StringBuilder(line.length());
            int pos = 0;

            for (pos = 0; pos < line.length(); pos++) {
                char c = line.charAt(pos);
                if (inExpression) {
                    if (c == '(') {
                        openCount += 1;
                        expression.append(c);
                        continue;
                    } else if (c == ')') {
                        if (openCount > 0) {
                            openCount--;
                            expression.append(c);
                        } else {
                            newLine.append(eval(expression.toString()));
                            expression = new StringBuilder();
                            inExpression = false;
                            changed = true;
                            openCount = 0;
                            continue;
                        }
                    } else {
                        expression.append(c);
                        continue;
                    }
                } else {
                    if (c == '(') {
                        inExpression = true;
                    } else {
                        newLine.append(c);
                    }
                }
            }
            if (changed) {
                // message("expanded: ", line, " to ", newLine.toString());
                return newLine.toString();
            } // else
            return line;
        }

        public boolean process(Action thisAction, String in_scene) {
            String line = expandAllOnLine(thisAction.args);
            line = evaluateBrackets(line);
            WordList argv = new WordList(line, tokens);
            scene = in_scene;
            params = new ParamList(argv, format);
            return doProcess();
        }

        public String resolveTag(String in_tag) {
            if (in_tag.contains(":")) {
                // already scoped
                return in_tag;
            }
            if (!scene.isEmpty()) {
                return scene + ":" + in_tag;
            } // else
            return in_tag;
        }

        protected abstract boolean doProcess();

        public String help() {
            return (helpInfo);
        }
    }


    class RandomCommand extends Command {

        RandomCommand() {
            keywords = List.of("random", "rand");
            helpInfo = "random variable [from] start [to] [end] (sets varible to random integer)";
            format = "+/name ~/from +/start ~/to ?/end";
        }

        public boolean doProcess() {
            int start = params.asInt("start");
            String name = params.get("name");
            if (params.isEmpty("end")) {
                vars.put(name, String.valueOf((int) random(start)));
            } else {
                int end = params.asInt("end");
                vars.put(name, String.valueOf((int) random(start, end)));
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

        public boolean doProcess() {
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

        public boolean doProcess() {
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

        public boolean doProcess() {
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
            format = "+/filename ~/as ?/tag ~/split ?/cols ?/by ?/rows";
        }

        public boolean doProcess() {
            String filename = params.get("filename");
            String tag = params.get("tag");
            String extension = "";
            int dot = filename.lastIndexOf('.');
            if (dot > 0) {
                extension = filename.substring(dot + 1);
            }
            if (tag.isEmpty()) {
                tag = filename.substring(0, dot - 1);
            }
            tag = resolveTag(tag);
            switch (extension.toLowerCase()) {
                case "jpg":
                case "jpeg":
                case "png":
                case "gif":
                    if (!images.containsKey(tag)) {
                        int cols = params.asInt("cols");
                        int rows = params.asInt("rows");
                        if (cols > 0 && rows > 0) {
                            images.put(tag, new SpriteImage(filename, cols, rows));
                        } else {
                            images.put(tag, new SpriteImage(filename));
                        }
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

        public boolean doProcess() {
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

        public boolean doProcess() {
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

        public boolean doProcess() {
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

        public boolean doProcess() {
            String tagList = params.get("rest");
            for (String tag : splitTokens(tagList, tokens)) {
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

        public boolean doProcess() {
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

        public boolean doProcess() {
            String tag = params.get("tag");
            String method = params.get("to");
            int depth = params.asInt("depth", -1);
            int direction = (task.equals("lower") || task.equals("drop")) ? -1 : 1;
            Sprite sprite = sprites.find(tag, scene);
            if (sprite == null) {
                return true;
            }
            if (depth < 0) {
                sprites.moveBy(sprite, direction);
            } else {
                if (method.equals("to")) {
                    sprites.setZ(sprite, depth * direction);
                } else if (method.equals("by")) {
                    sprites.moveBy(sprite, depth * direction);
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

        public boolean doProcess() {
            String itag = params.get("itag");
            String stag = params.get("stag");
            if (stag.isEmpty()) {
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
            if (w <= 0.0f) { // not given size
                sprites.add(new Sprite(itag, stag, x, y, z, scene));
            } else {
                sprites.add(new Sprite(itag, stag, x, y, z, w, h, scene));
            }
            return false;
        }
    }

    class CenterCommand extends Command {

        CenterCommand() {
            keywords = List.of("centre", "center");
            helpInfo = "center tag [at] x,y (set centre of rotation, in pixels relative to existing centre)";
            format = "+/tag ~/to +/x +/y";
        }

        public boolean doProcess() {
            Sprite sprite = sprites.find(params.get("tag"), scene);
            if (sprite != null) {
                int x = params.asInt("x");
                int y = params.asInt("y");
                sprite.setCenter(x, y);
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

        public boolean doProcess() {
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
            keywords = List.of("fade", "trans", "tint", "shade");
            helpInfo = "fade/tint tag to value [in time]";
            format = "+/tag ~/to +/trans ~/in ?/time";
        }

        public boolean doProcess() {
            Sprite sprite = sprites.find(params.get("tag"), scene);
            if (sprite != null) {
                float trans = params.asFloat("trans");
                float elapsed = params.asFloat("time");
                if (task.equals("fade") || task.equals("trans")) {
                    sprite.setGray(trans, elapsed);
                } else { // if (task.equals("trans")
                    sprite.setTint(trans, elapsed);
                }
            }
            return false;
        }
    }

    class RateCommand extends Command {

        RateCommand() {
            keywords = List.of("update", "rate");
            helpInfo = "rate tag per-second (set sprite update rate, per second";
            format = "+/tag ~/to +/rate ~/in ?/time";
        }

        public boolean doProcess() {
            Sprite sprite = sprites.find(params.get("tag"), scene);
            if (sprite != null) {
                float rate = params.asFloat("rate");
                float elapsed = params.asFloat("time");
                sprite.updateRate(rate, elapsed);
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

    public boolean doProcess() {
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

    public boolean doProcess() {
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

    public boolean doProcess() {
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

    public boolean doProcess() {  // set name as value
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

    public boolean doProcess() {  // set name as value
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

    public boolean doProcess() {  
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

    public boolean doProcess() {
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

public String expandVar(String word) {
    // built in variables
    // scene name? times of day?
    String value;
    switch (word) {
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
            value = String.valueOf((int) random(0, 100));
            break;
        case "RANDOMX":
            value = String.valueOf((int) random(0, width));
            break;
        case "RANDOMY":
            value = String.valueOf((int) random(0, height));
            break;
        default:
            value = "";
            break;
    }
    if (value.isEmpty() && vars.containsKey(word)) {
        return vars.get(word);
    }
    if (value.isEmpty()) {
        message("variable not found", word);
    }
    return value;
}

public String expandAllOnLine(String line) {
    if (line == null || line.isEmpty()) {
        return "";
    }
    StringBuilder varName = new StringBuilder();
    StringBuilder newLine = new StringBuilder(line.length());
    int pos = 0;
    String whiteSpace = " ,.\t;)(";

    boolean readingName = false;
    boolean inBraces = false;
    boolean changed = false;
    for(pos = 0; pos < line.length(); pos++) {
        char c = line.charAt(pos);
        if (c == '\\') { // only special before $
            if (pos + 1 < line.length() && line.charAt(pos + 1) == '$') {
                newLine.append('$');
                pos += 1;
                continue;
            }
        }
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
            newLine.append(c); // preserve what ended us, in case it was a bracket
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
    if (!varName.isEmpty()) {
        newLine.append(expandVar(varName.toString()));
    }
    if (changed) {
        // message("expanded: ", line, " to ", newLine.toString());
        return newLine.toString();
    } // else
    return line;
}

public void setVar(String name, String value) {
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
        commandList.add(new RateCommand());
        commandList.add(new PlayCommand());
        commandList.add(new FadeCommand());
        commandList.add(new HideCommand());
        commandList.add(new StopCommand());
        commandList.add(new StartCommand());
        commandList.add(new LoadCommand());
        commandList.add(new RandomCommand());
        commandList.add(new CalcCommand());
        commandList.add(new CenterCommand());
        commandList.add(new ReadCommand());
        commandList.add(new EchoCommand());
        commandList.add(new ExitCommand());
        commandList.add(new UnloadCommand());
        commandList.add(new DebugCommand());
    }

    public void dispatch(Action thisAction, String scene) {
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

    public void printHelp() {
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
        if (name.isEmpty()) {
            enabled = true;
        }
    }

    public void start() {
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

    public void stop() {
        if (name.isEmpty()) {
            // Must be top level, never stop this one!
            return;
        }
        enabled = false;
        sprites.stopScene(name);
        return;
    }

    public void end() {
        enabled = false;
        sceneActions = new ArrayList<Action>();
    }

    public void dump() {
        String state = enabled ? "Enabled" : "Disabled";
        String displayName = name == null ? "(top)" : name;
        println("Scene: " + displayName + " (from " + filename + ") - " + state);
        for(String line : content) {
            println(line);
        }
    }

    public void dumpActions() {
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

public void readFile(String filename) {
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
                if (!holding.isEmpty()) {
                    scenes.add(new Scene(currentScene, holding.toArray(new String[0]), filename));
                    holding = new ArrayList<String>();
                }
            }
        } else if (line.toLowerCase().startsWith("endscene")) {
            if (!holding.isEmpty()) {
                scenes.add(new Scene(currentScene, holding.toArray(new String[0]), filename));
                holding = new ArrayList<String>();
                currentScene = "";
            }
        } else if (line.toLowerCase().startsWith("include")) {
            String[] words = splitTokens(line, tokens);
            if (words.length < 2) {
                message("expected filename", filename, String.valueOf(lineCount));
            } else {
                for (String word : words) {
                    readFile(word);
                }
            }
        } else if (line.toLowerCase().startsWith("finish")) {
            // stop processing this file
            break;
        } else { // just add the line to the current scene
            if (currentScene.isEmpty()) {
                topLevel.add(line);
            } else {
                holding.add(line);
            }
        }
    }
    // finished reading file
    if (!topLevel.isEmpty()) {
        scenes.add(new Scene("", topLevel.toArray(new String[0]), filename));
    }
    if (!holding.isEmpty()) {
        scenes.add(new Scene(currentScene, holding.toArray(new String[0]), filename));
    }
}

public void readScript() {
    if (args != null) {
        for (String arg : args) {
            readFile(arg);
        }
    } else {
        readFile(scriptFile);
    }
    // Start running top level scene
    for(Scene scene : scenes) {
        if (scene.name.isEmpty()) {
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

public void doActions() {
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

public void setup() {
    commandProcessor = new CommandProcessor();
    readScript();
    colorMode(100,100);
    /* +++ONLY PDE
    fullScreen();
    +++ONLY PDE */
    imageMode(CENTER);
    frameRate(FRAMERATE);

};

public void draw() {
    // Actions have a resolution of 1 second, so only do them if the second changes
    if (thisSecond != second()) {
        thisSecond = second();
        doActions();
    }
    // but update sprites on every frame
    drawSprites();
};


/* +++END PDE+++ */

  public void settings() { size(SIZE_X, SIZE_Y); }

  static public void main(String[] passedArgs) {
    //String[] appletArgs = new String[] { "--full-screen", "--bgcolor=#666666", "--hide-stop", "anim_script" };
    String[] appletArgs = new String[] { "--bgcolor=#666666", "anim_script" };
    ArrayList<String> tempArgs = new ArrayList<String>();
    if (passedArgs != null) {
        for (String arg: passedArgs) {
            if (arg.startsWith("--width=")) {
                SIZE_X = Integer.parseInt(arg.substring(8));
            } else if (arg.startsWith("--height=")) {
                SIZE_Y = Integer.parseInt(arg.substring(9));
            } else {
                tempArgs.add(arg);
            }
        }
        String[] sendArgs = new String[tempArgs.size()];
        sendArgs = tempArgs.toArray(sendArgs);
      PApplet.main(concat(appletArgs, sendArgs));
    } else {
      PApplet.main(appletArgs);
    }
  }
}
