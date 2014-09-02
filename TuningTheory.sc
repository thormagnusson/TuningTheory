

// NOTE the below might look strange, but it's important to separate MIDI info (key-tuningreference) to the ratios (for key-dependent tunings)
// ratios[key-tuningreference]*tuningreference.midicps

TuningTheory {

	var win, gui, keybview, tuninggrid, notes, keyboardnotes, gridnotes, group, <tuningreference, tonic, pitchBend;
	var synthdefs, synth;
	var calcFreq, semitones, tuningratios, lastratiolisttext, nodestates, drawRatiosArray, lastkey;
	var <tuning, outbus, chordArray, <>noteRecArray;
		
	*new {
		^super.new.initKeystation;
	}

	initKeystation {
		
		tuningreference = 0; // by default in C
		tonic = 60;
		tuning = \et12;
		pitchBend = 1;
		outbus = 0;
		synthdefs = [\saw, \sine, \moog];
		synth = synthdefs[0];
		chordArray = []; // current notes
		noteRecArray = [];

		notes = Array.fill(1270, { nil }); // just make this very big (for 12+notesinanoctave tuning support)
		keyboardnotes = Array.fill(127, { nil }); // 
		nodestates = {{0}!12}!6;
		gridnotes = {{nil}!12}!6;

		gui = false; // no GUI by default

		MIDIIn.connectAll; // we connect all the incoming devices
		
		Server.default.waitForBoot({
			"MIDI Keyboard Ready for Play !!! ".postln;
			this.makeSynths();
			this.midiSetup();
			this.tuning_(tuning);
			group = Group.new; //  create a Group to be able to set cutoff of all active notes
		});

		CmdPeriod.add({ 			
			"CMD period".postln;
			notes = notes.collect({arg synth; if(synth != nil, { synth.release; synth = nil})  });
			gridnotes = gridnotes.collect({arg array; array.collect({arg synth; synth.release; }) });
			gridnotes = {{nil}!semitones.size}!6;
			group = Group.new;
		 });

	}
	
	checkPaths {
		 
		var pathToSclDir = Platform.userAppSupportDir+/+"scl/"; // the location of the Scala library
		var pathToUserSclDir =  Platform.userAppSupportDir+/+"scl_user/"; // user Scala scales library
		if(pathToSclDir.pathMatch==[], {
			("mkdir -p" + pathToSclDir.quote).unixCmd;
			"ixi-NOTE: a Scala scales folder was not found, it was created here:".postln;
			pathToSclDir.postln;
		});
		if(pathToUserSclDir.pathMatch==[], {
			("mkdir -p" + pathToUserSclDir.quote).unixCmd;
			"ixi-NOTE: an user Scala folder was not found, it was created here:".postln;
			pathToUserSclDir.postln;
		});
	}
	
	midiSetup {

		MIDIdef.noteOn(\myOndef, {arg vel, key, channel, device;
			// we use the key as index into the array as well
			keyboardnotes[key] = Synth(synth, [\out, outbus, \freq, this.calcFreq(key), \amp, vel/127, \cutoff, 10, \pitchBend, pitchBend], target:group);
			chordArray = chordArray.add(key);
			noteRecArray = noteRecArray.add(key); // just recording everything. User can clear and get at it through <>
			lastkey = key; 
			this.findChord(chordArray);
			if(gui, {	 {
				keybview.keyDown(key);
				this.setGridNode(key, 1);
				}.defer });
		});

		MIDIdef.noteOff(\myOffdef, {arg vel, key, channel, device;
			keyboardnotes[key].release;
			keyboardnotes[key] = nil;
			chordArray.remove(key);
			//this.findChord(chordArray);
			if( gui, { { 
				keybview.keyUp( key );
				this.setGridNode(key, 0);
				}.defer });
		});

		MIDIdef.cc(\myPitchBend, { arg val;
			pitchBend = val.linlin(0, 127, 0.5, 1.5);
			
			keyboardnotes.do({arg synth;
				if( synth != nil , { synth.set(\pitchBend, pitchBend ) });
			});
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
			keyboardnotes.do({arg synth;
				if( synth != nil , { synth.set(\vibrato, val.linlin(0, 127, 1, 20) ) });
			});
			notes.do({arg synth;
				if( synth != nil , { synth.set(\vibrato, val.linlin(0, 127, 1, 20) ) });
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
		this.tuning_(tuning);
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
		SynthDef(\sine, {arg out=0, freq=440, amp=0.5, gate=1, pitchBend=1, cutoff=20, vibrato=0;
			var signal, env;
			signal = SinOsc.ar([freq, freq]*pitchBend, 0, XLine.ar(0.7, 0.9, 0.13));
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
				notes[key] = Synth(synth, [\freq, this.calcFreq( key ), \amp, 0.5, \cutoff, 20, \pitchBend, pitchBend, \out, outbus ], target:group);
			});
		});
		
		gridnotes.copy.do({arg array, i;
			array.do({arg arraysynth, j;
				if( arraysynth != nil , { 
					arraysynth.release;
					gridnotes[i][j] = Synth(synth, [\freq, this.calcFreq( 24+(((i-6).abs*array.size)+j) ), \amp, 0.5, \cutoff, 20, \pitchBend, pitchBend, \out, outbus ], target:group);
				});
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
		var temptuning, keycounter, note;
		tuning = argtuning;
		
		if(tuning.isArray, {
			if(tuning[0] == 0, { // the tuning array is in cents (cents start with 0)
				tuningratios = (tuning/100).midiratio;
			}, {	// the array is in rational numbers (or floating point ratios from 1 to 2)
				tuningratios = tuning;
			});
			semitones = tuningratios.ratiomidi;
		}, {
			temptuning = Tuning.newFromKey(argtuning.asSymbol); 
			if(temptuning.isNil, { temptuning = XiiScala.new(argtuning.asSymbol) }); // support of the Scala scales / tunings
			tuningratios = temptuning.ratios;
			semitones = temptuning.semitones;
		});

		// keyboard notes
		keyboardnotes.do({arg synth, note;
			if( synth != nil , { synth.set(\freq, this.calcFreq( note ) ) });
		});

		if(gui, { 
			notes.do({arg synth, note;
				if( synth != nil , { synth.set(\freq, this.calcFreq( note ) ) });
			});			
			
			nodestates = tuninggrid.getNodeStates;
			this.createTuningGrid( semitones );
			
			tuninggrid.gridNodes.do({arg array;
				array.do({arg node;
					if( node.state == true, { 
						note = (node.nodeloc[0]+((node.nodeloc[1]-6).abs*semitones.size))+(semitones.size*2)+tuningreference;
						gridnotes[node.nodeloc[1]][node.nodeloc[0]].set(\freq, this.calcFreq( note ) );
					});
				});				
			});
		});
	}
		
	calcFreq {arg note;
	
//		^semitones[(note%semitones.size)].midicps * [1,2,4,8,16,32,64,128,256,512].at(note.div(semitones.size));

		^((semitones++semitones)[((note-tuningreference)+semitones.size)%semitones.size]+tuningreference).midicps * [1,2,4,8,16,32,64,128,256,512].at((note-tuningreference).div(semitones.size));

	}
	
	findChord { arg chordArr;
		var thischord, original, orgnl;
		if(chordArr.size>2, {
			original = chordArr.sort;
			thischord = ((chordArr-chordArr.minItem)%12).as(Set).as(Array).sort; // converting to Set temp - get rid of duplicates (e.g. oct above)
			block{|break| 
				XiiTuningTheory.chords.do({arg chord;
					orgnl = original.copy;
					thischord.size.do({arg i;
						if(chord[1] == thischord, {
							"Chord : ".post; 
							this.midinotenameSinOctave(orgnl[0]).post; 
							"-".post; 
							chord[0].post; 
							" - ".post; 
							chord[1].postln;
							break.value();
						}, {
							thischord[0] = thischord[0]+12; // rotate lowest key an octave up
							thischord = ((thischord-thischord.minItem)%12).sort;
							orgnl[0] = orgnl[0]+12;
							orgnl = orgnl.sort;
						});
					});
				});
				"Chord : unknown".postln;
			}
		});
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

		if(tuninggrid.isNil.not, { tuninggrid.remove }); // get rid of the existing grid
		tuninggrid = TuningGrid.new(win, bounds: Rect(10, 240, 990, 120), columns: semitones, rows: 6, border:true);
		tuninggrid.setBackgrColor_(Color.white);
		tuninggrid.setBorder_(true);
		tuninggrid.setTrailDrag_(true, true);
		tuninggrid.setFillMode_(true);
		tuninggrid.setFillColor_(Color.white);
		tuninggrid.setNodeStates_(nodestates);
		tuninggrid.setBackgrDrawFunc_({ // drawing harmonics
			Pen.color = Color.red(0.75);
			drawRatiosArray.do({arg ratio;
				Pen.line(Point((ratio.ratiomidi * (990/12)).round(1)+0.5, -10), Point((ratio.ratiomidi * (990/12)).round(1)+0.5, 320));
			});
			Pen.stroke;
		});
		tuninggrid.nodeDownAction_({arg nodeloc;
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
		tuninggrid.setState_(note%semitones.size, (note.div(semitones.size)-6).abs, state)
	}
		
	drawRatios {arg array;
		drawRatiosArray = array;
		tuninggrid.refresh;
	}

	// deprecated method
	setRatios {arg array;
		this.tuning_(array);
	}
	
	findRatios {arg array;
		^array.collect({arg ratio; 	(ratio.asFraction[0].asString +/+ ratio.asFraction[1].asString) });
	}
	
	midinotename {arg key;
		^["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"][key%12]++((key/12).floor-2).asInt;
	}

	midinotenameSinOctave {arg key;
		^["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"][key%12];
	}

	createGUI {
		var midiclientmenu, synthdefmenu, outbusmenu, tuningmenu, pitchCircle, fundNoteString, fString, scaleOrChord, scaleChordString;
		var chordmenu, scalemenu, play, mousesynth, tuningreftext;
		var chords, scales, tunings, chordnames, chord, scalenames, scale;
		var playMode, playmodeSC;
		var ratiowin, scalewin, ratiotext;

		var bounds = Rect(20, 5, 1200, 370);
		gui = true;
		
		drawRatiosArray = [];
		lastkey = 60;
		playMode = true;
		playmodeSC = "chord";
		win = Window.new("- tuning theory -", Rect(100, 500, bounds.width+20, bounds.height+10), resizable:false).front;

		keybview = MIDIKeyboard.new(win, Rect(10, 70, 990, 160), 5, 36)
				.keyDownAction_({arg key; 
					fString.string_(key.asString++"  :  "++this.midinotename(key));
					lastkey = key;
					if(playMode, {
						this.setGridNode(key, 1);
						noteRecArray = noteRecArray.add(key); // just recording everything. User can clear and get at it through <>
						mousesynth = Synth(synth, [\freq, this.calcFreq(key), \out, outbus, \pitchBend, pitchBend]);
					}, {
						tonic = key; 
//						tuningreference = tonic % 12; // this is the reference for non-et12 tempered scale
						pitchCircle.drawSet(chord, tonic%12);
						notes = notes.collect({arg synth; if(synth != nil, {synth.release; synth = nil }) });
						tuninggrid.clearGrid;
						keybview.showScale(chord, tonic, Color.new255(103, 148, 103));
						scaleChordString.string_(this.midinotenameSinOctave(tonic+chord));
						chord.do({arg degree; 
							var chordkey = degree + key;
							notes[chordkey] = Synth(synth, [\out, outbus, \freq, this.calcFreq(chordkey), \amp, 0.5, \cutoff, 20, \pitchBend, pitchBend], target:group);
							this.setGridNode(chordkey, 1);
						});
						chord.postln;
					});
				})
				.keyTrackAction_({arg key; tonic = key; 
					//fString.string_(key.asString++"  :  "++key.midinotename);
					lastkey = key;
					tuninggrid.clearGrid;
					if(playMode, {
						mousesynth.set(\gate, 0);
						this.setGridNode(lastkey, 0);
						this.setGridNode(key, 1);
						mousesynth = Synth(synth, [\freq, this.calcFreq(key), \out, outbus, \pitchBend, pitchBend]);
					},{	
						notes = notes.collect({arg synth; if(synth != nil, {synth.release; synth = nil}) });
						pitchCircle.drawSet(chord, tonic%12);
						chord.do({arg degree; 
							var chordkey = degree + key;
							notes[chordkey] = Synth(synth, [\out, outbus, \freq, this.calcFreq(chordkey), \amp, 0.5, \cutoff, 20, \pitchBend, pitchBend], target:group);
							this.setGridNode(chordkey, 1);
						});
						keybview.showScale(chord, tonic, Color.new255(103, 148, 103));
						scaleChordString.string_(this.midinotenameSinOctave(tonic+chord));
					});
				})
				.keyUpAction_({arg key; 
					if(playMode, {
						mousesynth.set(\gate, 0); 
						this.setGridNode(key, 0);
					}, {
						//tonic = key;
						keybview.showScale(chord, tonic, Color.new255(103, 148, 103));
						scaleChordString.string_(this.midinotenameSinOctave(tonic+chord));
//						notes = notes.collect({arg synth; if(synth != nil, {synth.release; synth = nil}) });
//						tuninggrid.clearGrid;
					});
				});
		
		this.createTuningGrid(semitones);		

		midiclientmenu = PopUpMenu.new(win,Rect(10,15,150,16))
				.font_(Font.new("Helvetica", 9))
				.canFocus_(false)
				.items_(MIDIClient.sources.collect({arg item; item.device + item.name}))
				.value_(0)
				.background_(Color.white)
				.action_({arg item;
					MIDIClient.sources.do({ |src, i| MIDIIn.disconnect(i, i) });
					MIDIIn.connect(item.value, MIDIClient.sources.at(item.value));
				});
		
		synthdefmenu = PopUpMenu.new(win,Rect(10,41,100,16))
				.font_(Font.new("Helvetica", 9))
				.items_(synthdefs)
				.value_(synthdefs.indexOf(synth))
				.background_(Color.white)
				.action_({arg item;
					this.synth_(synthdefs[item.value].asSymbol);
				});
		
		outbusmenu = PopUpMenu.new(win,Rect(115,41,45,16))
				.font_(Font.new("Helvetica", 9))
				.items_({|i| ((i*2).asString++","+((i*2)+1).asString)}!26)
				.value_(0)
				.background_(Color.white)
				.canFocus_(false)
				.action_({arg item;
					outbus = item.value * 2;
				});
		
		pitchCircle = XiiTuningPitchCircle.new(12, size:200, win: win);
		
		fundNoteString = StaticText.new(win, Rect(700, 15, 100, 20)).string_("tonic :")
						.font_(Font.new("Helvetica", 9));
						
		fString = StaticText.new(win, Rect(750, 15, 50, 20))
					.string_(tonic.asString++"  -  "++this.midinotename(tonic))
					.font_(Font.new("Helvetica", 9));
		
		scaleOrChord = StaticText.new(win, Rect(700, 40, 100, 20)).string_("chord :")
						.font_(Font.new("Helvetica", 9));
		scaleChordString = StaticText.new(win, Rect(750, 40, 250, 20))
						.string_(tonic.asString++"  -  "++this.midinotename(tonic))
						.font_(Font.new("Helvetica", 9));
		
		chords = XiiTuningTheory.chords;
		scales = XiiTuningTheory.scales;
		tunings = XiiTuningTheory.tunings; // reads from a custom file (or defaults to basic tunings)
		
		chordnames = [];
		chords.do({arg item; chordnames = chordnames.add(item[0])});
		chord = chords[0][1];
		
		scalenames = [];
		scales.do({arg item; scalenames = scalenames.add(item[0])});
		scale = scales[0][1];
		
		chordmenu = PopUpMenu.new(win,Rect(180,15,100,16))
				.font_(Font.new("Helvetica", 9))
				.items_(chordnames)
				.background_(Color.white)
				.action_({arg item;
					play.states_([["play chord", Color.black, Color.white]]);
					chord = chords[item.value][1];
					scaleOrChord.string_("Chord :");
					scaleChordString.string_(this.midinotenameSinOctave(tonic+chord));
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
		
		scalemenu = PopUpMenu.new(win,Rect(180,41,100,16))
				.font_(Font.new("Helvetica", 9))
				.items_(scalenames)
				.background_(Color.white)
				.action_({arg item;
					play.states_([["play scale", Color.black, Color.white]]);
					chord = scales[item.value][1];
					scaleOrChord.string_("Scale :");
					scaleChordString.string_(this.midinotenameSinOctave(tonic+chord));
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
		
		[\tunings, tunings].postln;
		[\tuning, tuning].postln;
		
		tuningmenu = PopUpMenu.new(win,Rect(300,41,100,16))
				.font_(Font.new("Helvetica", 9))
				.items_(tunings)
				.value_(tunings.collect({|tun|tun.asSymbol}).indexOf(tuning))
				.mouseDownAction_({arg view;
					view.items_(tunings); // this is needed as new tunings can be created
				})
				.background_(Color.white)
				.action_({arg item;
					tuning = tunings[item.value];
					this.tuning_(tuning.asSymbol);
					win.refresh;
				})
				.keyDownAction_({arg view, key, mod, unicode; 
					if (unicode == 13, { play.valueAction_(1) });
					if (unicode == 16rF700, { view.valueAction_(view.value-1) });
					if (unicode == 16rF703, { view.valueAction_(view.value-1) });
					if (unicode == 16rF701, { view.valueAction_(view.value+1) });
					if (unicode == 16rF702, { view.valueAction_(view.value+1) });
				});
		
		TuningRadioButton.new(win, Rect(300, 15, 16, 16), "play mode")
			.font_(Font.new("Helvetica", 9))
			.value_(1)
			.color_(Color.white)
			.action_({arg sl; 
				playMode = sl.value.booleanValue;
			});
		
		play = Button.new(win,Rect(420,15,90,18))
			.font_(Font.new("Helvetica", 9))
			.states_([["play scale", Color.black, Color.white]])
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
							key = key + tonic;
							a = Synth(synth, [\freq, this.calcFreq(key), \out, outbus, \pitchBend, pitchBend]);
							0.3.wait;
							a.release}.fork;
							0.3.wait;
						});
					})
				}).start;
			});

		Button.new(win,Rect(520,15,120,18))
			.font_(Font.new("Helvetica", 9))
			.states_([["make last note tuning ref", Color.black, Color.white]])
			.action_({
				[\tonic, tonic].postln;
				[\lastkey, lastkey].postln;
				tuningreference = lastkey % 12; // this is the reference for non-et12 tempered scale
				[\tuningreference, tuningreference].postln;
				tuningreftext.string_("tuning ref : "++ lastkey.asString +":"+ this.midinotename(lastkey));
			});
		
		tuningreftext = StaticText.new(win, Rect(534, 40, 120, 20)).string_("tuning ref : 60 - C3")
						.font_(Font.new("Helvetica", 9));

		
		Button.new(win, Rect(420,41,90,18))
			.font_(Font.new("Helvetica", 9))
			.canFocus_(false)
			.states_([["tuning ratios", Color.black, Color.white]])
			.action_({
				var ratiolisttext, lastselection, degreeslotmenu, roundmultiple;
				var tuningslider, tuningnumber, fineslider = 1;
				var slot=1;
				var offset = tuningratios[slot];
				
				roundmultiple = 1e-13;
				ratiowin = Window.new("ratios", Rect(win.bounds.left, win.bounds.top-220, 600, 190)).front;
				ratiotext= TextView.new(ratiowin, Rect(10, 10, 580, 50));
				ratiotext.string_(this.findRatios(tuningratios).asString);
				ratiotext.mouseDownAction_({ lastratiolisttext = ratiolisttext.string });
				ratiotext.keyDownAction_({arg view, key, modifiers, unicode, keycode;
					lastselection = "ratiotext";
					if((keycode == 36) || (keycode == 13), { // ENTER or evaluate (Shift+ENTER)
						this.tuning_(ratiotext.string.interpret);
						ratiolisttext.string_(tuningratios.asCompileString);
						offset = tuningratios[slot];
						tuningslider.value_(0.5);
					});
				});
				ratiolisttext= TextView.new(ratiowin, Rect(10, 70, 580, 50));
				ratiolisttext.string = tuningratios.asCompileString;
				ratiolisttext.mouseDownAction_({ lastratiolisttext = ratiolisttext.string });
				ratiolisttext.keyDownAction_({arg view, key, modifiers, unicode, keycode;
					lastselection = "ratiolisttext";
					if((keycode == 36) || (keycode == 13), { // ENTER or evaluate (Shift+ENTER)
						this.tuning_(ratiolisttext.string.interpret);
						ratiotext.string_(this.findRatios(tuningratios).asString);
						offset = tuningratios[slot];
						tuningslider.value_(0.5);
					});
				});
				degreeslotmenu = PopUpMenu.new(ratiowin,Rect(10,133, 30,16))
					.font_(Font.new("Helvetica", 9))
					.items_({arg i; (i+1).asString}!(tuningratios.size-1))
					.background_(Color.white)
					.action_({arg item;
						slot = item.value+1;
						offset = tuningratios[slot];
						tuningslider.value_(0.5);
					});
				tuningslider = Slider.new(ratiowin, Rect(45, 132, 250,16))
					.value_(0.5)
					.mouseDownAction_({ lastratiolisttext = ratiolisttext.string })
					.keyDownAction_({arg view, key, mod, unicode; 
						if(unicode == 32, { fineslider = 0.1 }); // the resolution of the slider
					})
					.keyUpAction_({arg view, key, mod, unicode; 
						if(unicode == 32, { fineslider = 1 });
					})
					.action_({arg sl;
						tuningratios[slot] = (offset + sl.value.linlin(0, 1, -0.01*fineslider, 0.01*fineslider)).round(roundmultiple);
						ratiotext.string_(this.findRatios(tuningratios).asString);
						ratiolisttext.string_(tuningratios.asCompileString);
						this.tuning_(ratiotext.string.interpret);
					});
				StaticText.new(ratiowin, Rect(10, 155, 100, 20)).string_("ratio res :")
						.font_(Font.new("Helvetica", 9));
				PopUpMenu.new(ratiowin,Rect(60, 158, 30,16))
					.font_(Font.new("Helvetica", 9))
					.items_(["3","4","5","6","7","8","9","10","11","12","13"])
					.value_(10)
					.background_(Color.white)
					.action_({arg menu;
						roundmultiple = ("1e-"++(menu.value+3)).interpret; // 0.001, 0.0001, 0.00001, etc.
					});
				Button.new(ratiowin, Rect(150, 155, 70, 20))
					.font_(Font.new("Helvetica", 9))
					.states_([["undo", Color.black, Color.white]])
					.action_({
						this.tuning_(lastratiolisttext.interpret);
						ratiolisttext.string_(tuningratios.asCompileString);
						ratiotext.string_(this.findRatios(tuningratios).asString);
						tuningslider.value_(0.5);
						offset = tuningratios[slot];
					});
				Button.new(ratiowin, Rect(225, 155, 70, 20))
					.font_(Font.new("Helvetica", 9))
					.states_([["confirm", Color.black, Color.white]])
					.action_({
						if(lastselection == "ratiotext", {
							ratiolisttext.string_(tuningratios.asCompileString);
							this.tuning_(ratiotext.string.interpret);
						}, {
							ratiotext.string_(this.findRatios(ratiolisttext.string.interpret).asString);
							this.tuning_(ratiolisttext.string.interpret);
							tuningslider.value_(0.5);
							offset = tuningratios[slot];
						});
					});
				Button.new(ratiowin, Rect(315, 130, 65, 20))
					.font_(Font.new("Helvetica", 9))
					.states_([["get ratios", Color.black, Color.white]])
					.action_({
						ratiotext.string_(this.findRatios(tuningratios).asString);
						ratiolisttext.string_(tuningratios.asCompileString);
						degreeslotmenu.items_({arg i; (i+1).asString}!(tuningratios.size-1));
					});
				Button.new(ratiowin, Rect(385, 130, 65, 20))
					.font_(Font.new("Helvetica", 9))
					.states_([["post cents", Color.black, Color.white]])
					.action_({
						" ------- Current tuning in cents ------- ".postln;
						((tuningratios.ratiomidi.round(0.00001) *100)).postln;
				});
//				Button.new(ratiowin, Rect(445, 130, 65, 20))
				Button.new(ratiowin, Rect(315, 155, 65, 20))
					.font_(Font.new("Helvetica", 9))
					.states_([["draw ratios", Color.black, Color.white]])
					.action_({
						this.drawRatios(ratiotext.string.interpret);
					});
				Button.new(ratiowin, Rect(385, 155, 65, 20))
					.font_(Font.new("Helvetica", 9))
					.states_([["try ratios", Color.black, Color.white]])
					.action_({
						if(lastselection == "ratiotext", {
							ratiolisttext.string_(tuningratios.asCompileString);
							this.tuning_(ratiotext.string.interpret);
						}, {
							ratiotext.string_(this.findRatios(ratiolisttext.string.interpret).asString);
							this.tuning_(ratiolisttext.string.interpret);
							tuningslider.value_(0.5);
							offset = tuningratios[slot];
						});
					});
				 Button.new(ratiowin, Rect(500, 130, 90, 20))
					.font_(Font.new("Helvetica", 9))
					.states_([["edit tuningmenu", Color.black, Color.white]])
					.action_({arg butt;
						var tuningfilepath = Platform.userAppSupportDir+/+"scl_user/_tuningmenu.scd";						("open "++tuningfilepath.quote).unixCmd;
					});
				 Button.new(ratiowin, Rect(500, 155, 90, 20))
					.font_(Font.new("Helvetica", 9))
					.states_([["create Scala file", Color.black, Color.white]])
					.action_({arg butt;
						var scaletext, scaletrybutt, scalesavebutt, scalastring;
						scalewin = Window.new("scala tuning", Rect(ratiowin.bounds.left+ratiowin.bounds.width+10, win.bounds.top-430, 610, 400)).front;
						scaletext= TextView.new(scalewin, Rect(10, 10, 590, 350));
						Button.new(scalewin, Rect(10, 370, 120, 20))
										.font_(Font.new("Helvetica", 9))
										.states_([["Scala information", Color.black, Color.white]])
										.action_({
											Document.new.string_("\nTHE SCALA FILE FORMAT \n\nSee info here: http://www.huygens-fokker.org/scala/scl_format.html\n
Here is an example of a valid file. Note:\n
- the first line is the scale name
- the third line is the description of the scale
- the fourth line is the number of degrees in the scale
- then we have the individual ratios, line by line 
- the 1/1 (first degree) is skipped 
- it is possible to mix rational numbers and cents\n
----------------------------------------------
! meanquar.scl
!
1/4-comma meantone scale. Pietro Aaron's temperament (1523)
12
!
76.04900
193.15686
310.26471
5/4
503.42157
579.47057
696.57843
25/16
889.73529
1006.84314
1082.89214
2/1
----------------------------------------------\n
When you save a Scala file, it will saved under the name you give your scale in the first line.\nIt will be saved in the scl_user folder (as not to mix with the official Huygens-Fokker archive)").promptToSave_(false);
										});
						scaletrybutt = Button.new(scalewin, Rect(435, 370, 80, 20))
										.font_(Font.new("Helvetica", 9))
										.states_([["try tuning", Color.black, Color.white]])
										.action_({
											var scalefile, scalename;
											scalefile = File(Platform.userAppSupportDir+/+"scl_user/_temp.scl", "w");
											scalefile.write(scaletext.string);
											scalefile.close;
											this.tuning_(\_temp);
										});
						scalesavebutt = Button.new(scalewin, Rect(520, 370, 80, 20))
										.font_(Font.new("Helvetica", 9))
										.states_([["save tuning", Color.black, Color.white]])
										.action_({
											var scalefile, scalename;
											scalename = scaletext.string[2..scaletext.string.find(".scl")-1];
											[\scalename, scalename].postln;
											scalefile = File(Platform.userAppSupportDir+/+"scl_user/"++scalename++".scl", "w");
											scalefile.write(scaletext.string);
											scalefile.close;
											tunings = tunings.add(scalename);
											[\tunings, tunings].postln;
											//tuningmenu.items_( tunings );
										});

						scalastring = "! _temp.scl
!
Description of the _temp scale (args: num of steps, then second degree, up until the octave)\n"
++(tuningratios.size).asString++"\n!\n";
this.findRatios(tuningratios)[1..].do({arg ratio; scalastring = scalastring ++ ratio ++ "\n" });
scalastring = scalastring ++ "2/1";

						scaletext.string = scalastring;
			});
		});
		
		Button.new(win,Rect(1008, 320, 90, 18))
			.font_(Font.new("Helvetica", 9))
			.canFocus_(false)
			.states_([["clear keyboard", Color.black, Color.white]])
			.action_({
				notes = notes.collect({arg synth; if(synth != nil, { synth.release; synth = nil }) });
				keybview.clear;
			});
			
		Button.new(win,Rect(1008, 345, 90, 18))
			.font_(Font.new("Helvetica", 9))
			.canFocus_(false)
			.states_([["clear grid", Color.black, Color.white]])
			.action_({
				tuninggrid.clearGrid;
				this.drawRatios([]); // empty drawing harmonics list
				gridnotes.do({arg array; array.do({arg synth; synth.release }) });
				gridnotes = {{nil}!semitones.size}!6;
			});
			
		// plot the frequency of strings played
		win.view.keyDownAction_({|me, char|
			if(char == $a, {
				//{patRecButt.valueAction_(1)}.defer;
			})	
		});
		
		win.view.keyUpAction_({|me, char|
			if(char == $a, {

			})	
		});
		
		win.onClose_({
			"Good bye GUI !".postln;
		//	noteonresponder.remove;
		//	noteoffresponder.remove;
		//	recordarrayresp.remove;
		//	pattern.stop;
		//	metronome.stop;
			if(ratiowin.isClosed.not, { ratiowin.close });
			if(scalewin.isClosed.not, { scalewin.close });
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

a.tuningreference = 1 // d
a.tuningreference = 2 // d
a.tuningreference = 3 // d
a.tuningreference = 4 // d
a.tuningreference = 5 // d
a.tuningreference = 6 // d
a.tuningreference = 7 // d
a.tuningreference = 8 // d
a.tuningreference = 9 // d
a.tuningreference = 10 // d
a.tuningreference = 11 // d
a.tuningreference = 12 // d
a.tuningreference = 13 // d

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

a = TuningTheory.new
a.createGUI
a.tuning

a.tuning = \vallotti
a.tuning = [ 1/1, 16/15, 9/8, 6/5, 5/4, 4/3, 45/32, 3/2, 8/5, 5/3, 9/5, 15/8 ]

a.tuningreference = 2; // tuning is now in D (C is default - integers are half-notes)


a.postLists



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


a = TuningTheory.new
a.createGUI
a.tuning

a.tuning = \vallotti
a.tuning = [ 1/1, 16/15, 9/8, 6/5, 5/4, 4/3, 45/32, 3/2, 8/5, 5/3, 9/5, 15/8 ]

a.postLists


// play something on midi keyboard
a.noteRecArray // this is what you played
// record a new array of midinotes:
a.noteRecArray = []
// explore it
a.noteRecArray


*/
