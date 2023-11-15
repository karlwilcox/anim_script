# AnimScript

AnimScript is a simple sprite based animation tool for the Processing graphics and art tool, written in Java.

It was originally written to generate a randomly moving background for a collection of Lego Creator buildings but is not limited to this.

## Principles of operation

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
    show plane1
after 5 
    move plane1 to $WIDTH,$CENTERY in 15 
after 20 
    hide plane1
after 25 
    quit
```

Yay, animation! We have a sprite appearing at the lower left, then after a few seconds moving linearly to the right hand edge where it disappears.

The scripting language supports a variety of triggers and actions, see the Wiki for a full description.

## Implementation

The anim_script code exists in two forms, ```anim_script.java```, which is pure java implementation and the primary development source, it can for example be edited using Intellij Idea and run from the command line using java --run. Alternatively there is 
```anim_script.pde``` which can be opened in the Processing app and run using the 'Run' button. This file should *NOT* be edited by hand, it is auto-generated by the shell script java2pde.sh - this is a simple script controlled by specially formatted comments embedded within the Java source code. Looking at the source of the script, and the Java code itself should be sufficient to figure out how it all works.

## Example of Use

Below is an early prototype of the intended use, displayed on a thin screen monitor placed behind some Lego creator buildings on a narrow shelf, to give both the appearance of greater depth and a randomly occuring set of sounds and actions.




