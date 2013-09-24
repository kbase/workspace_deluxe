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
		string id;
		string name;
		string sequence;
		list <feature_id> feature_ids;
	} Genome;

	/*
	*/
	typedef structure {
		string name;
		string annotation;
		list <feature_id> feature_ids;
	} FeatureGroup;


};


