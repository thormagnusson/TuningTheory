/*

A SuperCollider support for reading Scala files:
http://www.xs4all.nl/~huygensf/scala/scl_format.html


a = DroneScale("bohlen-p_9");
a.tuning.octaveRatio
a.degrees
a.semitones
a.pitchesPerOctave


*/

XiiScala : Tuning {

	var <pitchesPerOctave;
	var pathToSclDir = "/Users/thm21/Library/Application Support/SuperCollider/scl/"; 
	// Platform.userAppSupportDir+/+"scl/"; // the location of the Scale library
	
	var pathToUserSclDir = "/Users/thm21/Library/Application Support/SuperCollider/scl_user/"; 
	// Platform.userAppSupportDir+/+"scl_user/"; // user collection of Scala scales
	
	*new { | scl |
		^super.new.readScl( scl.asString ); // convert it to string in case it's a symbol
	}

	readScl { | scl |
		var file, lines, line, ratios, num, degrees;

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
			if(line.contains(".").not, { // ratios
					num = line.interpret.ratiomidi;
					tuning = tuning ++ num;
				}, { // cents 
					num = line.asFloat;
					num = num / 100;
					tuning = tuning ++ num;
			});
		});

		tuning = tuning.addFirst(0); // the interval 1/1 is not explicitly stated in the .scl file
		octaveRatio = tuning.pop.midiratio;
		
		degrees = Array.series(pitchesPerOctave, 0, 1);
		^Scale(degrees, pitchesPerOctave, this, name);
	}
	
}
