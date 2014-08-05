

// NOTE the below might look strange, but it's important to separate MIDI info (key-tuningreference) to the ratios (for key-dependent tunings)
// ratios[key-tuningreference]*tuningreference.midicps

TuningTheory {

	var win, gui, keybview, tuninggrid, notes, gridnotes, group, tuningreference, tonic, pitchBend;
	var synthdefs, synth;
	var calcFreq, semitones, tuningratios, nodestates, drawRatiosArray;
	var <tuning, outbus, chordArray;
		
	*new {
		^super.new.initKeystation;
	}

	initKeystation {
		
		tuningreference = 0; // by default in C
		tonic = 60;
		pitchBend = 1;
		outbus = 0;
		synthdefs = [\saw, \moog];
		synth = synthdefs[0];
		chordArray = []; // current notes
	//	ratiowinFlag = false;
		
		gui = false; // no GUI by default

		MIDIIn.connectAll; // we connect all the incoming devices
		MIDIFunc.noteOn({arg ...x; x.postln; }); // we post all the args
		
		Server.default.waitForBoot({
			"MIDI Keyboard Ready for Play !!! ".postln;
			this.makeSynths();
			this.midiSetup();
			this.tuning_(\et12);
		});
	}
	
	midiSetup {
				
		notes = Array.fill(1270, { nil }); // just make this very big (for 12+notesinanoctave tuning support)
		nodestates = {{0}!12}!6;
		//gridnotes = Array.fill(10000, { nil });
		gridnotes = {{nil}!12}!6;
		group = Group.new; // we create a Group to be able to set cutoff of all active notes

		MIDIdef.noteOn(\myOndef, {arg vel, key, channel, device;
			// we use the key as index into the array as well
			notes[key] = Synth(synth, [\out, outbus, \freq, this.calcFreq(key), \amp, vel/127, \cutoff, 10, \pitchBend, pitchBend], target:group);
			chordArray = chordArray.add(key%12).sort;
			
			block{|break| XiiTheory.chords.do({arg chord; if(chord[1] == chordArray, {
				"Current Chord is : ".post; chord[0].postln;
				chord[1].postln;
				break.value();
				})}); };
				
			[\chordArray, chordArray].postln;
			if(gui, {	 {
				keybview.keyDown(key);
				this.setGridNode(key, 1);
				}.defer });
		});

		MIDIdef.noteOff(\myOffdef, {arg vel, key, channel, device;
			notes[key].release;
			notes[key] = nil;
			chordArray.remove(key%12);
			if( gui, { { 
				keybview.keyUp( key );
				this.setGridNode(key, 0);
				}.defer });
		});

		MIDIdef.cc(\myPitchBend, { arg val;
			pitchBend = val.linlin(0, 127, 0.5, 1.5);
			notes.do({arg synth;
				if( synth != nil , { synth.set(\pitchBend, pitchBend ) });
			});
			gridnotes.do({arg array;
				array.do({arg synth;
					if( synth != nil , { synth.set(\pitchBend, pitchBend ) });
				});
			});
		});

		MIDIdef.bend(\myVibrato, { arg val;
			notes.do({arg synth;
				if( synth != nil , {  });
			});
			gridnotes.do({arg array;
				array.do({arg synth;
					if( synth != nil , { synth.set(\vibrato, val.linlin(0, 127, 1, 20) ) });
				});
			});
		});
	}
	
	tuningreference_ {arg ton; // this is in MIDI standard, so 0 is C, 1 is C#, 2 is D, etc. (A is 9)
		tuningreference = ton;	
	}

	makeSynths {
		//First we create a synth definition for this example:
		SynthDef(\moog, {arg out=0, freq=440, amp=0.5, gate=1, pitchBend=1, cutoff=20, vibrato=0;
			var signal, env;
			signal = LPF.ar(VarSaw.ar([freq, freq+2]*pitchBend+SinOsc.ar(vibrato, 0, 1, 1), 0, XLine.ar(0.7, 0.9, 0.13)), (cutoff * freq).min(18000));
			env = EnvGen.ar(Env.adsr(0), gate, levelScale: amp, doneAction:2);
			Out.ar(out, signal*env);
		}).add;
		SynthDef(\saw, {arg out=0, freq=440, amp=0.5, gate=1, pitchBend=1, cutoff=20, vibrato=0;
			var signal, env;
			signal = LPF.ar(Saw.ar([freq, freq]*pitchBend, XLine.ar(0.7, 0.9, 0.13)), (cutoff * freq).min(18000));
			env = EnvGen.ar(Env.adsr(0), gate, levelScale: amp, doneAction:2);
			Out.ar(out, signal*env);
		}).add;
	}
	
	synth_ {arg stype;
		var keycounter = 36;
		synth = stype;
		notes.copy.do({arg arraysynth, key;
			if( arraysynth != nil , { 
				arraysynth.release;
				notes[key] = Synth(synth, [\freq, this.calcFreq( key ), \amp, 0.5, \cutoff, 10, \pitchBend, pitchBend, \out, outbus ], target:group);
			});
		});
	// XXX needs fixing
		gridnotes.copy.reverse.do({arg array, i;
			array.do({arg arraysynth, j;
				if( arraysynth != nil , { 
					arraysynth.release;
					gridnotes[i][j] = Synth(synth, [\freq, this.calcFreq( keycounter ), \amp, 0.5, \cutoff, 10, \pitchBend, pitchBend, \out, outbus ], target:group);
				});
				keycounter = keycounter + 1;
			});
		});

	}
	
	postLists {
		"".postln;
		\n__NODESTATES______________________.postln;
		Post << nodestates;	
		"".postln;
		\__GRIDNOTES______________________.postln;
		Post << gridnotes;	
	}
	
	tuning_ { | argtuning |
		var temptuningratios, keycounter, note;
		tuning = argtuning;
		
		if(tuning.isArray, {
			if(tuning[0] == 0, { // the tuning array is in cents (cents start with 0)
				tuningratios = (tuning/100).midiratio;
			}, {	// the array is in rational numbers (or floating point ratios from 1 to 2)
				tuningratios = tuning;
			});
			semitones = tuningratios.ratiomidi;
		}, {
			temptuningratios = Tuning.newFromKey(argtuning.asSymbol); 
			if(temptuningratios.isNil, { temptuningratios = XiiScala.new(argtuning) }); // support of the Scala scales / tunings
			tuningratios = temptuningratios.ratios;
			semitones = temptuningratios.semitones;
		});
		
		//semitones = semitones ++ (semitones+12); // I need an extra octave of semitones, as tuningreference can go up 11 halftones
//		keycounter = semitones.size*3; // third octave up
		keycounter = 36; // third octave up
		
		// keyboard notes
		notes.do({arg synth, note;
			if( synth != nil , { synth.set(\freq, this.calcFreq( note ) ) });
		});

		if(gui, { 
			nodestates = tuninggrid.getNodeStates;
			this.createTuningGrid( semitones );
			//tuninggrid.setNodeStates_( nodestates );
		//	[\semitones___________________, semitones].postln;
		//	[\nodestates___________________, nodestates].postln;
			// notegrid notes
//			gridnotes.reverse.do({arg array;
//				array.do({arg synth;
//					if( synth != nil , { synth.set(\freq, this.calcFreq(keycounter) ) });
//					keycounter = keycounter + 1;
//				})
//			});
			tuninggrid.gridNodes.do({arg array;
				array.do({arg node;
					if( node.state == true, { 
						note = (node.nodeloc[0]+((node.nodeloc[1]-6).abs*semitones.size))+(semitones.size*2)+tuningreference;
						gridnotes[node.nodeloc[1]][node.nodeloc[0]].set(\freq, this.calcFreq( note ) );
					});
				});				
			});
			
//			if(ratiowinFlag, {
//				"In Here".postln;
//				// put a list with rational numbers into the window
//				ratiotext.string_(this.findRatios(tuningratios).asString);
//			});
	
		});
	}
		
	calcFreq {arg note;
		\_________________________________________________.postln;
	//	[\Semitones, semitones].postln;
	//	[\note, note].postln;
	//	[\tuningreference, tuningreference].postln;
	//	[\semitone, (semitones++semitones)[((note-tuningreference)+semitones.size)%semitones.size]].postln; // this is CORRECT !!!
	//	[\freq, ((semitones++semitones)[((note-tuningreference)+semitones.size)%semitones.size] + tuningreference).midicps].postln;
	
//		^semitones[(note%semitones.size)].midicps * [1,2,4,8,16,32,64,128,256,512].at(note.div(semitones.size));

		^((semitones++semitones)[((note-tuningreference)+semitones.size)%semitones.size]+tuningreference).midicps * [1,2,4,8,16,32,64,128,256,512].at((note-tuningreference).div(semitones.size));

	}

	createTuningGrid {arg semitones;
		// grid increasing in size
		if(nodestates[0].size < semitones.size, {
			nodestates = nodestates.collect({arg array;
				array ++ ({0}!(semitones.size-nodestates.size))
			});
			gridnotes = gridnotes.collect({arg array;
				array ++ ({nil}!(semitones.size-nodestates.size))
			});
		}, { // grid decreasing in size
			nodestates = nodestates.collect({ arg array;
				array[0 .. semitones.size];
			});
			gridnotes.do({arg array; array.do({arg synth, i; if(i>semitones.size, { synth.release })})});
			gridnotes = gridnotes.collect({arg array;
				array[0 .. semitones.size];
			});
		});			
	//	[\NODESTATES_, nodestates].postln;
		
		tuninggrid = TuningGrid.new(win, bounds: Rect(10, 230, 990, 120), columns: semitones, rows: 6, border:true);
		tuninggrid.setBackgrColor_(Color.white);
		tuninggrid.setBorder_(true);
		tuninggrid.setTrailDrag_(true, true);
		tuninggrid.setFillMode_(true);
		tuninggrid.setFillColor_(Color.white);
		tuninggrid.setNodeStates_(nodestates);
		tuninggrid.setBackgrDrawFunc_({ // drawing harmonics
//			Pen.color = Color.green(0.75);
//			drawRatiosArray.do({arg ratio;
//				Pen.line(Point((ratio-1)*990, 0), Point((ratio-1)*990, 300));
//			});
//			Pen.stroke;
			Pen.color = Color.red(0.75);
			drawRatiosArray.do({arg ratio;
				Pen.line(Point((ratio.ratiomidi * (990/12)).round(1)+0.5, -10), Point((ratio.ratiomidi * (990/12)).round(1)+0.5, 320));
			});
			Pen.stroke;
		});
		tuninggrid.nodeDownAction_({arg nodeloc;
			var note = (nodeloc[0]+((nodeloc[1]-6).abs*semitones.size))+(semitones.size*2)+tuningreference;
//			[\nodeloc, nodeloc].postln; 
//			[\note, note].postln;
			if(tuninggrid.getState(nodeloc[0], nodeloc[1]) == 1, {
//				[\currentNODE, gridnotes[nodeloc[1]][nodeloc[0]]].postln;
				gridnotes.postln;
				if(gridnotes[nodeloc[1]][nodeloc[0]].isNil, {
					gridnotes[nodeloc[1]][nodeloc[0]] = Synth(synth, [\freq, this.calcFreq( note ), \out, outbus, \pitchBend, pitchBend]);
				});
			}, {
				gridnotes[nodeloc[1]][nodeloc[0]].release;
				gridnotes[nodeloc[1]][nodeloc[0]] = nil;
			});
		});
		tuninggrid.nodeTrackAction_({arg nodeloc;
			var note = (nodeloc[0]+((nodeloc[1]-6).abs*semitones.size))+(semitones.size*2)+tuningreference;
			if(tuninggrid.getState(nodeloc[0], nodeloc[1]) == 1, {
				if(gridnotes[nodeloc[1]][nodeloc[0]].isNil, {
					gridnotes[nodeloc[1]][nodeloc[0]] = Synth(synth, [\freq, this.calcFreq( note ), \out, outbus, \pitchBend, pitchBend]);
				});
			}, {
				gridnotes[nodeloc[1]][nodeloc[0]].release;
				gridnotes[nodeloc[1]][nodeloc[0]] = nil;
			});
		});
	}

	setGridNode {arg argnote, state=1;
		var node, note, offset;
		offset = 24; // should this be called keycounter?
		note = argnote-offset;
		node = [note%semitones.size, (note.div(semitones.size)-6).abs];
		[\node, node].postln;
		tuninggrid.setState_(note%semitones.size, (note.div(semitones.size)-6).abs, state)
	}
	
	test {arg note, state;
		this.setGridNode(note, state);	
	}
	
	
	drawRatios {arg array;
		drawRatiosArray = array;
		tuninggrid.refresh;
	}

	setRatios {arg array;
		this.tuning_(array);
	}
	
	findRatios {arg array;
		^array.collect({arg ratio; 	(ratio.asFraction[0].asString +/+ ratio.asFraction[1].asString) });
	}
	
	createGUI {
		var midiclientmenu, synthdefmenu, outbusmenu, tuningmenu, pitchCircle, fundNoteString, fString, scaleOrChord, scaleChordString;
		var chordmenu, scalemenu, play, patRecButt, mousesynth;
		var chords, scales, tunings, chordnames, chord, scalenames, scale;
		var playMode, playmodeSC, lastkey;
		var ratiowin, ratiotext;

		var bounds = Rect(20, 5, 1200, 360);
		gui = true;
		
		drawRatiosArray = [];
		lastkey = 60;
		playMode = true;
		playmodeSC = "chord";
		win = Window.new("- ixi tuning theory -", Rect(100, 500, bounds.width+20, bounds.height+10), resizable:false).front;
		
		keybview = MIDIKeyboard.new(win, Rect(10, 60, 990, 160), 5, 36)
				.keyDownAction_({arg key; 
					fString.string_(key.asString++"  :  "++key.midinotename);
					if(playMode, {
						key.postln;
						[\freq, this.calcFreq(key), \tonic, tonic].postln;
						this.setGridNode(key, 1);

						mousesynth = Synth(synth, [\freq, this.calcFreq(key), \out, outbus, \pitchBend, pitchBend]);
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
						tuningreference = tonic % 12; // this is the reference for non-et12 tempered scales
						[\tuningreference, tonic].postln;
						
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
						this.setGridNode(lastkey, 0);
						this.setGridNode(key, 1);
						lastkey = key;
						//(note.midicps)*tuningratios.wrapAt(note-(tonic%12)).postln;
						mousesynth = Synth(synth, [\freq, this.calcFreq(key), \out, outbus, \pitchBend, pitchBend]);
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
					this.setGridNode(key, 0);
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
		
		this.createTuningGrid(semitones);		

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
		
		
		tuningmenu = PopUpMenu.new(win,Rect(300,31,100,16))
				.font_(Font.new("Helvetica", 9))
				.items_(tunings.collect({arg tuning; tuning[0]}))
				.background_(Color.white)
				.action_({arg item;
					//tuning = tunings[item.value][1];
					tuning = tunings[item.value][0].asSymbol;
					//nodestates = tuninggrid.getNodeStates;
					[\nodestates____, nodestates].postln;
					this.tuning_(tuning);

					//tuninggrid.calculateDrawing(semitones);
					//tuninggrid.setNodeStates_(nodestates);
					[\tuning, tuning].postln;
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
							a = Synth(synth, [\freq, this.calcFreq(key), \out, outbus, \pitchBend, pitchBend]);
							0.35.wait;
							a.release}.fork;
							0.4.wait;
						});
						0.6.wait;
						chord.do({arg key;
							{var a;
							key = key + tonic;
							a = Synth(synth, [\freq, this.calcFreq(key), \out, outbus, \pitchBend, pitchBend]);
							0.8.wait;
							a.release}.fork;
						});
					}, {
						tempchord = chord ++ 12;
						tempchord.mirror.do({arg key;
							{var a;
							key = key + 48;
							a = Synth(synth, [\freq, this.calcFreq(key), \out, outbus, \pitchBend, pitchBend]);
							0.3.wait;
							a.release}.fork;
							0.3.wait;
						});
					})
				}).start;
			});
		
		patRecButt = Button.new(win,Rect(420,31,90,16))
			.font_(Font.new("Helvetica", 9))
			.states_([["create tuning", Color.black, Color.clear]])
//			.states_([["record pattern", Color.black, Color.clear], 
//					["recording", Color.black, Color.red.alpha_(0.2)], 
//					["playing pattern", Color.black, Color.green.alpha_(0.2)]])
			.action_({arg butt;
				var scalewin, scaletext, scaletrybutt, scalesavebutt;
				scalewin = Window.new("scala tuning", Rect(10, 10, 600, 400)).front;
				scaletext= TextView.new(scalewin, Rect(10, 10, 580, 350));
				scaletrybutt = Button.new(scalewin, Rect(360, 370, 100, 20))
								.font_(Font.new("Helvetica", 9))
								.states_([["Try Tuning", Color.black, Color.clear]])
								.action_({
									// XXX need to allow for testing scales before saving
									var scalefile, scalename;
//									scalename = scaletext.string[2..scaletext.string.find(".scl")-1];
//									[\scalename, scalename].postln;
									scalefile = File(Platform.userAppSupportDir+/+"scl_user/_temp.scl", "w");
									scalefile.write(scaletext.string);
									scalefile.close;
									this.tuning_(\_temp);
									tuningmenu.items_(tunings.collect({arg tuning; tuning[0]}) ++ [\_temp] )
								});
				scalesavebutt = Button.new(scalewin, Rect(480, 370, 100, 20))
								.font_(Font.new("Helvetica", 9))
								.states_([["Save Tuning", Color.black, Color.clear]])
								.action_({
									var scalefile, scalename;
									scalename = scaletext.string[2..scaletext.string.find(".scl")-1];
									[\scalename, scalename].postln;
									scalefile = File(Platform.userAppSupportDir+/+"scl_user/"++scalename++".scl", "w");
									scalefile.write(scaletext.string);
									scalefile.close;
									tunings = tunings.add([scalename.asString, scalename.asSymbol]);
									tuningmenu.items_(tunings.collect({arg tuning; tuning[0]}) ++ [scalename.asSymbol] )
								});
				
//				scaletext.string = "! _temp.scl
//!
//Description of the _temp scale (args: num of steps, then second degree, up until the octave)
//12
//!
//567/512
//9/8
//147/128
//21/16
//1323/1024
//189/128
//3/2
//49/32
//7/4
//441/256
//63/32
//2/1
//";

				scaletext.string = "! _temp.scl
!
Description of the _temp scale (args: num of steps, then second degree, up until the octave)
22
!
256/243
16/15
10/9
9/8
32/27
6/5
5/4
81/64
4/3
27/20
45/32
729/512
3/2
128/81
8/5
5/3
27/16
16/9
9/5
15/8
243/128
2/1
";

				
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
		
		Button.new(win,Rect(1010,315,90,16))
			.font_(Font.new("Helvetica", 9))
			.states_([["tuning ratios", Color.black, Color.clear]])
			.action_({
				var ratiolisttext, lastselection;
				var tuningslider, tuningnumber;
				var slot=1;
				var offset = tuningratios[slot];
				
				ratiowin = Window.new("ratios", Rect(win.bounds.left, win.bounds.top-230, 600, 200)).front;
				ratiotext= TextView.new(ratiowin, Rect(10, 10, 580, 50));
				ratiotext.string_(this.findRatios(tuningratios).asString);
				ratiotext.keyDownAction_({arg view, key, modifiers, unicode, keycode;
					[\view, view, \key, key, modifiers, unicode, keycode].postln;
					lastselection = "ratiotext";
					if(keycode == 36, { // ENTER
						"ENTER".postln;
						ratiolisttext.string_(tuningratios.asString);
						this.setRatios(ratiotext.string.interpret);
						offset = tuningratios[slot];
						tuningslider.value_(0.5);
					});
				});
				ratiolisttext= TextView.new(ratiowin, Rect(10, 70, 580, 50));
				ratiolisttext.string = tuningratios.asString;
				ratiolisttext.keyDownAction_({arg view, key, modifiers, unicode, keycode;
					[\view, view, \key, key, modifiers, unicode, keycode].postln;
					lastselection = "ratiolisttext";
					if(keycode == 36, { // ENTER
						"ENTER".postln;
						ratiotext.string_(this.findRatios(tuningratios).asString);
						this.setRatios(ratiolisttext.string.interpret);
						offset = tuningratios[slot];
						tuningslider.value_(0.5);
					});
				});
			//	ratiowinFlag = true;
			//	ratiowin.onClose({ ratiowinFlag = false });
		[\items, 	{arg i; i}!tuningratios.size].postln;
				PopUpMenu.new(ratiowin,Rect(10,130, 30,16))
					.font_(Font.new("Helvetica", 9))
					.items_({arg i; (i+1).asString}!(tuningratios.size-1))
					.background_(Color.white)
					.action_({arg item;
						slot = item.value+1;
						offset = tuningratios[slot];
						tuningslider.value_(0.5);
					//	tuningnumber.value_(tuningratios[slot]);
						[\slot, slot].postln;
					});
				tuningslider = Slider.new(ratiowin, Rect(45,130, 200,16))
					.value_(0.5)
					.action_({arg sl;
						tuningratios[slot] = offset + sl.value.linlin(0, 1, -0.01, 0.01);
						ratiotext.string_(this.findRatios(tuningratios).asString);
						ratiolisttext.string_(tuningratios.asString);
					//	this.drawRatios(ratiotext.string.interpret);
						this.setRatios(ratiotext.string.interpret);
					});
//				tuningnumber = NumberBox.new(ratiowin, Rect(200, 130, 60,16))
//					.value_(tuningratios[slot])
//					.step_(0.0001)
//					.scroll_step_(0.0001)
//					.action_({arg box;
//						
//						tuningslider.value_(box.value.linlin(tuningratios[slot]-0.01, tuningratios[slot]+0.01, 0, 1));
//						tuningratios[slot] = box.value;
//						ratiotext.string_(this.findRatios(tuningratios).asString);
//					//	this.drawRatios(ratiotext.string.interpret);
//						this.setRatios(ratiotext.string.interpret);
//					});
				Button.new(ratiowin, Rect(275, 130, 70, 20))
					.font_(Font.new("Helvetica", 9))
					.states_([["post cents", Color.black, Color.clear]])
					.action_({
						" ------- Current tuning in cents ------- ".postln;
						((tuningratios.ratiomidi.round(0.00001) *100)).postln;
				});
				Button.new(ratiowin, Rect(355, 130, 70, 20))
					.font_(Font.new("Helvetica", 9))
					.states_([["get ratios", Color.black, Color.clear]])
					.action_({
						ratiotext.string_(this.findRatios(tuningratios).asString);
						ratiolisttext.string_(tuningratios.asString);
					});
				Button.new(ratiowin, Rect(430, 130, 70, 20))
					.font_(Font.new("Helvetica", 9))
					.states_([["draw ratios", Color.black, Color.clear]])
					.action_({
						this.drawRatios(ratiotext.string.interpret);
					});
				Button.new(ratiowin, Rect(505, 130, 70, 20))
					.font_(Font.new("Helvetica", 9))
					.states_([["try ratios", Color.black, Color.clear]])
					.action_({
						if(lastselection == "ratiotext", {
					ratiolisttext.string_(tuningratios.asString);
							this.setRatios(ratiotext.string.interpret);
						}, {
					ratiotext.string_(this.findRatios(ratiolisttext.string.interpret).asString);
					this.setRatios(ratiolisttext.string.interpret);
						tuningslider.value_(0.5);
						offset = tuningratios[slot];
						});
					});
			});
			
		Button.new(win,Rect(1010, 335, 90, 16))
			.font_(Font.new("Helvetica", 9))
			.states_([["clear grid", Color.black, Color.clear]])
			.action_({
				tuninggrid.clearGrid;
				this.drawRatios([]); // empty drawing harmonics list
				gridnotes.do({arg array; array.do({arg synth; synth.release }) });
				gridnotes = {{nil}!12}!6;
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
			notes.do({arg synth; synth.release });
			gridnotes.do({arg array; array.do({arg synth; synth.release }) });
			Server.default.freeAll;
			gui = false;

		});
		
	}
}


/*

a = TuningTheory.new
a.createGUI

a.tuningreference = 0 // c
a.tuningreference = 2 // d
a.synth = \saw
a.synth = \moog
a.tuning = \just
a.tuning = \et12


a = TuningTheory.new
a.createGUI
a.postLists
// draw ratios
a.drawRatios([9/8, 81/64, 4/3, 3/2, 27/16, 243/128, 2/1])
a.drawRatios([]) // eraze lines that are already drawn
// set ratios (if not using GUI)
a.setRatios([9/8, 81/64, 4/3, 3/2, 27/16, 243/128, 2/1])



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
a.tuning = \arch_dor
a.tuning = \breed
a.tuning = \burt20
a.tuning = \cairo // ah! the problem is that I need to use the octaveRatio instead of 12 in drawing







*/
