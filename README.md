TuningTheory
============

A platform to experiment with tunings and scales

This is primarily created as a test ground for development and compositional work using the Threnoscope, but it should work well for teaching and experimental work exploring tunings and scales, using both a MIDI keyboard and a GUI.

For example:

a = TuningTheory.new

// change the type of synth used
a.synth = \saw

// change tuning system
a.tuning = \just
a.tuning = \et12
a.tuning = \pythagorean
a.tuning = \wcHarm

// La Monte Young's Well Tuned Piano
a.tuning = [1/1, 567/512, 9/8, 147/128, 21/16, 1323/1024, 189/128, 3/2, 49/32, 7/4, 441/256, 63/32]
a.tuning = [0, 177, 204, 240, 471, 444, 675, 702, 738, 969, 942, 1173] // the same in cents

// create a GUI
a.createGUI

