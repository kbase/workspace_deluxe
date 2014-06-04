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
	/* @range (0,30] */
	typedef float f3;
	/* @range ,30] */
	typedef float f4;
	/* @range 0, */
	typedef float f5;

	/* @range [5,] */
	typedef int i1;
	/* @range [,0) */
	typedef int i2;
	/* @range (0,10] */
	typedef int i3;
	/* @range 0,1 */
	typedef int i4;
	/* @range (0, */
	typedef int i6;
	
	/* 
	  @metadata ws f1
	  @optional f1 f2 f3 f4 f5 i1 i2 i3 i4 i5 i6
	*/
	typedef structure {
		f1 f1;
		f2 f2;
		f3 f3;
		f4 f4;
		f5 f5;
		i1 i1;
		i2 i2;
		i3 i3;
		i4 i4;
		int i5;
		i6 i6;
	} NumberObj;

	/* @range [-98765432101234567890,98765432101234567890] */
	typedef int bi1;
	/* @range (-98765432101234567890,98765432101234567890) */
	typedef int bi2;
	/* @range (-98765432101234567890, */
	typedef int bi3;
	/* @range ,98765432101234567890) */
	typedef int bi4;
	/* @range [-98765432101234567890, */
	typedef int bi5;
	/* @range ,98765432101234567890] */
	typedef int bi6;
	
	/*@range [0,1.0e99]*/
	/*
	note: this fails test only because perl encodes internally as 1.0e99 and java as 1.0E+99, which
	are equivalent.  So the test check needs fixing, not the range handling.
	typedef float bf1;
	*/
	
	/*@range [0,1.0E+99]*/
	typedef float bf1;
	
	/* @optional i1 i2 i3 i4 i5 i6 */
	typedef structure {
		bi1 i1;
		bi2 i2;
		bi3 i3;
		bi4 i4;
		bi5 i5;
		bi6 i6;
	} BigNumberObj;
	
	typedef structure {
		tuple <string,int,mapping<string,int>,float> t;
	} TupleObject;
};


