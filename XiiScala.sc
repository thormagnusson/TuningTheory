/*

A SuperCollider support for reading Scala files:
http://www.xs4all.nl/~huygensf/scala/scl_format.html


a = XiiScala("bohlen-p_9");
a.tuning.octaveRatio
a.degrees
a.semitones
a.pitchesPerOctave


*/

XiiScala {

	var <pitchesPerOctave, pathToSclDir, pathToUserSclDir;
	
	*new { | scl |
		^super.new.readScl( scl.asString ); // convert it to string in case it's a symbol
	}

	readScl { | scl |
		var file, lines, line, ratios, num, degrees;
		var tuning, tuningClass, octaveRatio, name;
		
		pathToSclDir = Platform.userAppSupportDir+/+"scl/"; // the location of the Scale library
		pathToUserSclDir =  Platform.userAppSupportDir+/+"scl_user/";
		
		tuning = [];
		lines = [];
		line = 0;
		if(File.exists(pathToSclDir ++ scl ++ ".scl"), {
			file = File.open( pathToSclDir ++ scl ++ ".scl" , "r" ); // read the .scl file
		}, {
			file = File.open( pathToUserSclDir ++ scl ++ ".scl" , "r" ); // read the .scl file
		});
		
		while ({ line.isNil.not }, {
			line = file.getLine; 
			if(line.isNil.not, { if(line.contains("!").not, { lines = lines.add(line) }) });
		});
		file.close;
		
		name = lines.removeAt(0);
		name = name.asString; // the first line will the the name
		pitchesPerOctave = lines.removeAt(0).asInteger;
		lines.do({|line|  // each scale pitch will be either in ratio or cents notation
			if(line.contains("/"), { // a rational number
				num = line.interpret.ratiomidi;
			}, { // cents 
				num = line.asFloat / 100;
			});
				tuning = tuning.add(num);
		});

		tuning = tuning.addFirst(0); // the interval 1/1 is not explicitly given in the .scl file
		octaveRatio = tuning.pop.midiratio;
	
		degrees = Array.series(pitchesPerOctave, 0, 1);
		tuningClass = Tuning.new(tuning, octaveRatio, name);

		^Scale(degrees, pitchesPerOctave, tuningClass, name);
	}
}
