/*
test module for type validation with annotations
*/
module KB {

	/*
	@id ws
	*/
	typedef string kbid;

	/*
	@id ws KB.Genome
	*/
	typedef string genome_id;

	/*
	@id ws KB.Feature
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
	@searchable ws_subset id feature_ids
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


	typedef structure {
		string name;
		UnspecifiedObject stuff;
	} RandomObject;


	/* @range [-12.5,30.3] */
	typedef float f1;
	/* @range [0,30) */
	typedef float f2;

	/* @range [5,] */
	typedef int i1;
	/* @range [,0) */
	typedef int i2;
	/* @range (0,10] */
	typedef int i3;
	/* @range 0,1 */
	typedef int i4;
	
	/* @optional f1 f2 i1 i2 i3 i4 */
	typedef structure {
		f1 f1;
		f2 f2;
		i1 i1;
		i2 i2;
		i3 i3;
		i4 i4;
	} NumberObj;

};


