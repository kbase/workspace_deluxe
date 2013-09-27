/*
test module for type validation with annotations
*/
module KB {


	/*
	@id_reference
	*/
	typedef string kbid;

	/*
	@id_reference KB.Genome
	*/
	typedef string genome_id;

	/*
	@id_reference KB.Feature
	*/
	typedef string feature_id;

	/*
	*/
	typedef structure {
		string id;
		string name;
		string type;
		int length;
	} Feature;

	/*
	
	*/
	typedef structure {
		genome_id genome;
		string description;
		list <feature_id> interesting_things;
	} RelatedGenome;

	/*
	*/
	typedef structure {
		string id;
		string name;
		string sequence;
		list <feature_id> feature_ids;
		mapping <feature_id, int> length_of_features;
		list <RelatedGenome> related_genomes;
	} Genome;

	/*
	*/
	typedef structure {
		string name;
		string annotation;
		list <feature_id> feature_ids;
	} FeatureGroup;


};


