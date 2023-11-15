# AnimScript

AnimScript is a simple sprite based animation tool for the Processing graphics and art tool, written in Java.

It was originally written to generate a randomly moving background for a collection of Lego Creator buildings but is not limited to this.

Principles of operation

Rather than writing Processing Java code directly, we write a “script” (just a plain text file) that contains one or more “actions”. Actions happen in response to a number of “triggers”. A minimal script file could be:

```# AnimScript File 1
begin 
    load background.jpg as background

after 3
    place background at $CENTERX,$CENTERY depth 99 size $WIDTH,$HEIGHT
    show background
```
This script will display a blank screen, after 3 seconds display the background image, filling the screen. Okay, so this hasn’t saved us much from just writing the actual code, som let’s try something a little more advanced:

```
# AnimScript File 2
begin load background.jpg as background
    load plane.jpg as plane1
    place background at $CENTERX,$CENTERY,99 size $WIDTH,$HEIGHT
    show background
after 3 
    place plane1 at 0,$HEIGHT,50 size 100,50
    show plane
after 5 
    move plane1 to $WIDTH,$CENTERY in 15 
after 20 
    hide plane1
after 25 
    quit
```

Yay, animation! We have a sprite appearing at the lower left, then after a few seconds moving linearly to the right hand edge where it disappears.

The scripting language supports a variety of triggers and actions, see the Wiki for a full description.
