
TuningTheory {

	var gui, keybview, notes, group, ratios, tuningtonic, tonic, pitchBend;
	var synthdefs, synth;
	var calcFreq;
	var tuning, outbus;
		
	*new {
		^super.new.initKeystation;
	}

	initKeystation {
		
		tuningtonic = 0; // by default in C
		tonic = 60;
		pitchBend = 1;
		outbus = 0;
		synthdefs = [\saw, \moog];
		synth = synthdefs[0];
		
		gui = false; // no GUI by default
		
		this.tuning_(\et12);

		MIDIIn.connectAll; // we connect all the incoming devices
		MIDIFunc.noteOn({arg ...x; x.postln; }); // we post all the args
		
		Server.default.waitForBoot({
			"MIDI Keyboard Ready for Play !!! ".postln;
			this.makeSynths();
			this.midiSetup();
		});
	}
	
	synth_ {arg stype;
		synth = stype;
		notes.copy.do({arg arraysynth, key;
			if( arraysynth != nil , { 
				arraysynth.release;
				notes[key] = Synth(synth, [\freq, ratios[key]*tuningtonic.midicps, \amp, 0.5, \cutoff, 10, \pitchBend, pitchBend, \out, outbus ], target:group);
			});
		});
	}
					
	tuning_ { | argtuning |
		var temptuningratios, tuningratios;
		tuning = argtuning;
		
		if(tuning.isArray, {
			if(tuning[0] == 0, { // the tuning array is in cents (cents start with 0)
				tuningratios = (tuning/100).midiratio;
			}, {	// the array is in rational numbers (or floating point ratios)
				tuningratios = tuning;
			});
		}, {
			temptuningratios = Tuning.newFromKey(argtuning.asSymbol); 
			if(temptuningratios.isNil, { temptuningratios = XiiScala.new(argtuning) }); // support of the Scala scales / tunings
			tuningratios = temptuningratios.ratios;
		});
		[\tuningratios, tuningratios].postln;
		
		//tuningsize = tuningratios.size; // needed to know how many ratios there are in the tuning system
		ratios = Array.fill(10, {|i| tuningratios*2.pow(i) }).flatten;

		notes.do({arg synth, key;
			if( synth != nil , { synth.set(\freq, ratios[key]*tuningtonic.midicps ) });
		});
		
	}
	
	tuningtonic_ {arg ton; // this is in MIDI standard, so 0 is C, 1 is C#, 2 is D, etc. (A is 9)
		tuningtonic = ton;	
	}

	makeSynths {
		
			//First we create a synth definition for this example:
		SynthDef(\moog, {arg freq=440, amp=1, gate=1, pitchBend=1, cutoff=20, vibrato=0;
			var signal, env;
			signal = LPF.ar(VarSaw.ar([freq, freq+2]*pitchBend+SinOsc.ar(vibrato, 0, 1, 1), 0, XLine.ar(0.7, 0.9, 0.13)), (cutoff * freq).min(18000));
			env = EnvGen.ar(Env.adsr(0), gate, levelScale: amp, doneAction:2);
			Out.ar(0, signal*env);
		}).add;

		SynthDef(\saw, {arg freq=440, amp=1, gate=1, pitchBend=1, cutoff=20, vibrato=0;
			var signal, env;
			signal = LPF.ar(Saw.ar([freq, freq]*pitchBend, XLine.ar(0.7, 0.9, 0.13)), (cutoff * freq).min(18000));
			env = EnvGen.ar(Env.adsr(0), gate, levelScale: amp, doneAction:2);
			Out.ar(0, signal*env);
		}).add;

	
	}
	
	midiSetup {
				
		notes = Array.fill(127, { nil });
		group = Group.new; // we create a Group to be able to set cutoff of all active notes

		calcFreq = {arg key; ratios[key]*tuningtonic.midicps };
		
		MIDIdef.noteOn(\myOndef, {arg vel, key, channel, device;
			
			var freq = calcFreq.value(key);
			// we use the key as index into the array as well
			[\midi, key].postln;
			[\ratio, ratios[key]*tuningtonic.midicps].postln;
			[\keymidicps, key.midicps].postln;
			[\keymidicps, key.midicps].postln;
			
			notes[key] = Synth(synth, [\out, outbus, \freq, freq, \amp, vel/127, \cutoff, 10, \pitchBend, pitchBend], target:group);
			if(gui, {	 {keybview.keyDown(key)}.defer });
		});
		MIDIdef.noteOff(\myOffdef, {arg vel, key, channel, device;
			notes[key].release;
			notes[key] = nil;
			if(gui, {	 {keybview.keyUp(key)}.defer });
		});

		MIDIdef.cc(\myPitchBend, { arg val;
			pitchBend = val.linlin(0, 127, 0.5, 1.5);
			notes.do({arg synth;
				if( synth != nil , { synth.set(\pitchBend, pitchBend ) });
			});
		});

		MIDIdef.bend(\myVibrato, { arg val;
			notes.do({arg synth;
				if( synth != nil , { synth.set(\vibrato, val.linlin(0, 127, 1, 20) ) });
			});
		});
	
	}
	
	createGUI {
		
		var win, midiclientmenu, synthdefmenu, outbusmenu, pitchCircle, fundNoteString, fString, scaleOrChord, scaleChordString;
		var chordmenu, scalemenu, play, patRecButt, mousesynth;
		var chords, scales, tunings, chordnames, chord, scalenames, scale;
		var playMode, playmodeSC;
		
		var bounds = Rect(20, 5, 1000, 222);
		gui = true;
		
		
								tuningtonic = 0; // by default in C
								tonic = 60;
								pitchBend = 1;
								outbus = 0;
								synthdefs = [\saw, \moog];
								synth = synthdefs[0];
		
		gui = false; // no GUI by default

		
		playMode = true;
		playmodeSC = "chord";
		win = Window.new("- ixi pattern maker -", Rect(400, 400, bounds.width+20, bounds.height+10), resizable:false).front;
		
		keybview = MIDIKeyboard.new(win, Rect(10, 60, 790, 160), 4, 36)
				.keyDownAction_({arg key; 
					fString.string_(key.asString++"  :  "++key.midinotename);
					if(playMode, {
						key.postln;
						[\freq, ratios[key]*tuningtonic.midicps, \tonic, tonic].postln;
						mousesynth = Synth(synth, [\freq, ratios[key]*tuningtonic.midicps, \out, outbus]);
//							if(timerReadyFlag, { 
//								timerReadyFlag = false;
//								thistime = TempoClock.default.beats;
//							});
//							if(noteRecFlag, {
//								freqArray = freqArray.add((note.trunc(12)+(tuning.semitones[note%12])).midicps);
//								ampArray = ampArray.add(0.2); // rounding - no need for 5 tail numbers
//								timesincelastkey = (TempoClock.default.beats-thistime).round(0.25);
//								thistime = TempoClock.default.beats;
//								durArray = durArray.add(timesincelastkey);
//								sustainDict.add(note -> thistime.copy); // EXP
//								sustainArray = sustainArray.add(nil); // EXP - this is a dummy to be replaced
//							});

					}, {
						tonic = key; 
						pitchCircle.drawSet(chord, tonic%12);
						keybview.showScale(chord, tonic, Color.new255(103, 148, 103));				scaleChordString.string_((tonic+chord).midinotename.asString);
						chord.postln;
					});
				})
				.keyTrackAction_({arg key; tonic = key; 
					//fString.string_(key.asString++"  :  "++key.midinotename);
					mousesynth.set(\gate, 0);
					if(playMode, {
						key.postln;
						//(note.midicps)*tuningratios.wrapAt(note-(tonic%12)).postln;
						mousesynth = Synth(synth, [\freq, ratios[key]*tuningtonic.midicps, \out, outbus]);
//							if(timerReadyFlag, { 
//								timerReadyFlag = false;
//								thistime = TempoClock.default.beats;
//							});
//							if(noteRecFlag, {
//								freqArray = freqArray.add((note.trunc(12)+(tuning.semitones[note%12])).midicps);
//								ampArray = ampArray.add(0.2); // rounding - no need for 5 tail numbers
//								timesincelastkey = (TempoClock.default.beats-thistime).round(0.25);
//								thistime = TempoClock.default.beats;
//								durArray = durArray.add(timesincelastkey);
//								sustainDict.add(note -> thistime.copy); // EXP
//								sustainArray = sustainArray.add(nil); // EXP - this is a dummy to be replaced
//							});
					},{	
						keybview.showScale(chord, tonic, Color.new255(103, 148, 103));
						scaleChordString.string_((tonic+chord).midinotename.asString);
					});
				})
				.keyUpAction_({arg key; 
					var downtime;
					mousesynth.set(\gate, 0); 
					//fString.string_(key.asString++"  :  "++key.midinotename);
					if(playMode, {
//							if(noteRecFlag, {
//								downtime = sustainDict.at(note);
//								//[\downtime, downtime].postln;
//								timesincelastkey = (TempoClock.default.beats-downtime).round(0.25);
//								//[\timesincelastkey, timesincelastkey].postln;
//								//if((timesincelastkey == 0) || (timesincelastkey < 0), {timesincelastkey = 0.125});
//								// sustainArray = sustainArray.add(timesincelastkey);
//								sustainArray = sustainArray.collect({arg item, i; 
//												if((item==nil) && ((note.trunc(12)+(tuning.semitones[note%12])).midicps==freqArray[i]),
//													 { timesincelastkey }, 
//													 {item}); 
//											});
//							});
						
					},{
						tonic = key;
						keybview.showScale(chord, tonic, Color.new255(103, 148, 103));
						scaleChordString.string_((tonic+chord).midinotename.asString);
					});
				});
		
		
		midiclientmenu = PopUpMenu.new(win,Rect(10,5,150,16))
				.font_(Font.new("Helvetica", 9))
				.items_(MIDIClient.sources.collect({arg item; item.device + item.name}))
				.value_(0)
				.background_(Color.white)
				.action_({arg item;
					MIDIClient.sources.do({ |src, i| MIDIIn.disconnect(i, i) });
					MIDIIn.connect(item.value, MIDIClient.sources.at(item.value));
				});
		
		synthdefmenu = PopUpMenu.new(win,Rect(10,31,100,16))
				.font_(Font.new("Helvetica", 9))
				.items_(synthdefs)
				.value_(synthdefs.indexOf(synth))
				.background_(Color.white)
				.action_({arg item;
					this.synth_(synthdefs[item.value].asSymbol);

				//	synth = synthdefs[item.value].asSymbol;
				//	"synth is : ".post; synth.postln;
//					if(patternPlaying, {
//								pattern = Pdef(\pattern,
//									Pbind(
//										\instrument, synthname, 
//										\freq, Pseq(freqArray, inf), 
//										\dur, Pseq(durArray, inf),
//										\amp, Pseq(ampArray, inf), 
//										\sustain, Pseq(sustainArray, inf),
//										\out, outbus
//										)
//									).play(quant:4);
//									
//								" ************  Generated Pattern : ".postln;
//								("Pbind(\\instrument, " ++ "\\" ++ synthname.asString ++ ", \\freq, Pseq(" ++ freqArray.asString ++ ", inf), \\dur, Pseq(" ++ durArray.asString ++ ", inf), \\amp, Pseq(" ++ ampArray.asString ++ ", inf), \\sustain, Pseq(" ++ sustainArray.asString ++", inf)).play" ).postln;
//					});
				});
		
		outbusmenu = PopUpMenu.new(win,Rect(115,31,45,16))
				.font_(Font.new("Helvetica", 9))
				.items_({|i| ((i*2).asString++","+((i*2)+1).asString)}!26)
				.value_(0)
				.background_(Color.white)
				.action_({arg item;
					outbus = item.value * 2;
					"outbus is : ".post; outbus.postln;
				});
		
		pitchCircle = XiiTuningPitchCircle.new(12, size:200, win: win);
		
		fundNoteString = StaticText.new(win, Rect(540, 5, 100, 20)).string_("tonic :")
						.font_(Font.new("Helvetica", 9));
						
		fString = StaticText.new(win, Rect(590, 5, 50, 20))
					.string_(tonic.asString++"  -  "++tonic.midinotename)
					.font_(Font.new("Helvetica", 9));
		
		scaleOrChord = StaticText.new(win, Rect(540, 30, 100, 20)).string_("Chord :")
						.font_(Font.new("Helvetica", 9));
		scaleChordString = StaticText.new(win, Rect(590, 30, 250, 20))
						.string_(tonic.asString++"  -  "++tonic.midinotename)
						.font_(Font.new("Helvetica", 9));
		
		chords = XiiTheory.chords;
		scales = XiiTheory.scales;
		tunings = XiiTheory.tunings;
		
		chordnames = [];
		chords.do({arg item; chordnames = chordnames.add(item[0])});
		chord = chords[0][1];
		
		scalenames = [];
		scales.do({arg item; scalenames = scalenames.add(item[0])});
		scale = scales[0][1];
		
		chordmenu = PopUpMenu.new(win,Rect(180,5,100,16))
				.font_(Font.new("Helvetica", 9))
				.items_(chordnames)
				.background_(Color.white)
				.action_({arg item;
					(tonic%12).postln;
					play.states_([["play chord", Color.black, Color.clear]]);
					chord = chords[item.value][1];
					scaleOrChord.string_("Chord :");
					scaleChordString.string_((tonic+chord).midinotename.asString);
					keybview.showScale(chord, tonic, Color.new255(103, 148, 103));
					playmodeSC = "chord";
					pitchCircle.drawSet(chord, tonic%12);
					chord.postln;
					win.refresh;
				})
				.keyDownAction_({arg view, key, mod, unicode; 
					if (unicode == 13, { play.valueAction_(1) });
					if (unicode == 16rF700, { view.valueAction_(view.value-1) });
					if (unicode == 16rF703, { view.valueAction_(view.value-1) });
					if (unicode == 16rF701, { view.valueAction_(view.value+1) });
					if (unicode == 16rF702, { view.valueAction_(view.value+1) });
				});
		
		scalemenu = PopUpMenu.new(win,Rect(180,31,100,16))
				.font_(Font.new("Helvetica", 9))
				.items_(scalenames)
				.background_(Color.white)
				.action_({arg item;
					play.states_([["play scale", Color.black, Color.clear]]);
					chord = scales[item.value][1];
					scaleOrChord.string_("Scale :");
					scaleChordString.string_((tonic+chord).midinotename.asString);
					keybview.showScale(chord, tonic, Color.new255(103, 148, 103));
					playmodeSC = "scale";
					pitchCircle.drawSet(chord, tonic%12);
					chord.postln;
					win.refresh;
				})
				.keyDownAction_({arg view, key, mod, unicode; 
					if (unicode == 13, { play.valueAction_(1) });
					if (unicode == 16rF700, { view.valueAction_(view.value-1) });
					if (unicode == 16rF703, { view.valueAction_(view.value-1) });
					if (unicode == 16rF701, { view.valueAction_(view.value+1) });
					if (unicode == 16rF702, { view.valueAction_(view.value+1) });
				});
		
		
		PopUpMenu.new(win,Rect(300,31,100,16))
				.font_(Font.new("Helvetica", 9))
				.items_(tunings.collect({arg tuning; tuning[0]}))
				.background_(Color.white)
				.action_({arg item;
					//tuning = tunings[item.value][1];
					tuning = tunings[item.value][0].asSymbol;
					this.tuning_(tuning);
					//tuningratios = (tuning-ratiosET) + 1;
					//tuningratios.postln;
					"Selectec tuning : ".post; tuning.postln;
					win.refresh;
				})
				.keyDownAction_({arg view, key, mod, unicode; 
					if (unicode == 13, { play.valueAction_(1) });
					if (unicode == 16rF700, { view.valueAction_(view.value-1) });
					if (unicode == 16rF703, { view.valueAction_(view.value-1) });
					if (unicode == 16rF701, { view.valueAction_(view.value+1) });
					if (unicode == 16rF702, { view.valueAction_(view.value+1) });
				});
		
		OSCIIRadioButton.new(win, Rect(300, 5, 12, 12), "play mode")
			.font_(Font.new("Helvetica", 9))
			.value_(1)
			.action_({arg sl; 
				playMode = sl.value.booleanValue;
				if(playMode, {
					keybview.clear;
					fundNoteString.string_("Note :")
				}, {
					fundNoteString.string_("tonic :")
				});
			});
		
		play = Button.new(win,Rect(420,5,90,16))
			.font_(Font.new("Helvetica", 9))
			.states_([["play scale", Color.black, Color.clear]])
			.action_({
				var tempchord;
				chord.postln;
				Task({
					if(playmodeSC == "chord", {
						chord.do({arg key;
							{var a;
							key = key + tonic;
							a = Synth(synth, [\freq, ratios[key]*tuningtonic.midicps, \out, outbus]);
							0.35.wait;
							a.release}.fork;
							0.4.wait;
						});
						0.6.wait;
						chord.do({arg key;
							{var a;
							key = key + tonic;
							a = Synth(synth, [\freq, ratios[key]*tuningtonic.midicps, \out, outbus]);
							0.8.wait;
							a.release}.fork;
						});
					}, {
						tempchord = chord ++ 12;
						tempchord.mirror.do({arg key;
							{var a;
							key = key + 48;
							a = Synth(synth, [\freq, ratios[key]*tuningtonic.midicps, \out, outbus]);
							0.3.wait;
							a.release}.fork;
							0.3.wait;
						});
					})
				}).start;
			});
		
		patRecButt = Button.new(win,Rect(420,31,90,16))
			.font_(Font.new("Helvetica", 9))
			.states_([["record pattern", Color.black, Color.clear], 
					["recording", Color.black, Color.red.alpha_(0.2)], 
					["playing pattern", Color.black, Color.green.alpha_(0.2)]])
			.action_({arg butt;
//				switch(butt.value)
//				{0}{"STANDBY".postln;
//						pattern.stop;
//						noteRecFlag = false;
//						timerReadyFlag = true;
//						patternPlaying = false;
//		
//					}
//				{1}{"RECORDING PATTERN".postln;
//								pattern.stop;
//								noteRecFlag = true;
//								timerReadyFlag = true;
//								patternPlaying = false;
//		
//					}
//				{2}{"PLAYING PATTERN".postln;
//					
//								if(freqArray.size > 0, { // there was some recording taking place
//								
//									" ************  Frequency array is : ".postln;
//									freqArray.postln;
//								
//									timesincelastkey = (TempoClock.default.beats-thistime).round(0.25);
//									thistime = TempoClock.default.beats;
//									durArray = durArray.add(timesincelastkey);
//									durArray.removeAt(0);
//									// quantizise to the bar
//									durArray[durArray.size-1] = durArray[durArray.size-1]+(durArray.sum.round(4)-durArray.sum);
//									
//									//detuneArray = noteArray.collect({arg note; note.midicps * tuningratios.wrapAt(note-(tonic%12)) - note.midicps });
//			
//									" ************  Detune array is : ".postln;
//									//detuneArray.postln;
//									
//									" ************  Duration array is : ".postln;
//									durArray.postln;
//				
//									" ************  Sustain array is : ".postln;
//									sustainArray.postln;
//				
//				
//									pattern = Pdef(\pattern,
//										Pbind(
//											\instrument, synthname, 
//											\freq, Pseq(freqArray, inf), 
//											\dur, Pseq(durArray, inf),
//											\amp, Pseq(ampArray, inf), 
//											\sustain, Pseq(sustainArray, inf),
//											\out, outbus
//											)
//										).play(quant:4);
//										
//									" ************  Generated Pattern : ".postln;
//									
//									Post << ("Pbind(\\instrument, " ++ "\\" ++ synthname.asString ++ ", \\freq, Pseq(" ++ freqArray.asCompileString ++ ", inf), \\dur, Pseq(" ++ durArray.asCompileString ++ ", inf), \\amp, Pseq(" ++ ampArray.asCompileString ++ ", inf), \\sustain, Pseq(" ++ sustainArray.asCompileString ++", inf), \\out, "++ outbus ++").play" );
//									
//									noteRecFlag = false;
//									freqArray = [];
//									durArray = [];
//									ampArray = [];
//									sustainArray = [];
//									timerReadyFlag = false;
//									patternPlaying = true;
//									
//								},{
//									pattern.stop;
//									patternPlaying = false;
//									timerReadyFlag = true;
//								});
//		
//		
//					};
			});
			
		// plot the frequency of strings played
		win.view.keyDownAction_({|me, char|
			if(char == $a, {
				{patRecButt.valueAction_(1)}.defer;
			})	
		});
		
		win.view.keyUpAction_({|me, char|
			if(char == $a, {
				" ************ your recorded frequency array is : ".postln;
				{patRecButt.valueAction_(2)}.defer;
		
		//		freqArray.postln;
		//		noteRecFlag = false;
		//		freqArray = [];
			})	
		});
		
		win.onClose_({
			"Good bye! - All responders removed".postln;
		//	noteonresponder.remove;
		//	noteoffresponder.remove;
		//	recordarrayresp.remove;
		//	pattern.stop;
		//	metronome.stop;
			Server.default.freeAll;
			gui = false;

		});
		
	}
}


/*

a = TuningTheory.new
a.createGUI

a.tuningtonic = 0 // c
a.tuningtonic = 2 // d
a.synth = \saw
a.synth = \moog
a.tuning = \just
a.tuning = \et12


a.tuning = \wcSJ
a.tuning = \mean5
a.tuning = \mean6
a.tuning = \sept1
a.tuning = \pythagorean
a.tuning = \wcHarm
// La Monte Young's Well Tuned Piano
a.tuning = [1/1, 567/512, 9/8, 147/128, 21/16, 1323/1024, 189/128, 3/2, 49/32, 7/4, 441/256, 63/32]
a.tuning = [0, 177, 204, 240, 471, 444, 675, 702, 738, 969, 942, 1173]
a.tuning = \vallotti
a.tuning = \ellis
a.tuning = \bohlen_12
a.tuning = \biggulp
a.tuning = \bailey_well








*/
