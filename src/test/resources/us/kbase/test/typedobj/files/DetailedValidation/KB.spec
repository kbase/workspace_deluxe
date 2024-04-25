/*
test module for type validation with annotations
*/
module KB {

	/* */
	typedef structure {
		string f;
	} StringField;
	
	/* */
	typedef structure {
		int f;
	} IntField;
	
	/* */
	typedef structure {
		float f;
	} FloatField;
	
	/* */
	typedef structure {
		list<string> f;
	} ListField;
	
	/* */
	typedef structure {
		mapping<string,string> f;
	} MappingField;
	
	/* */
	typedef structure {
		tuple<string,string,string,int> f;
	} TupleField;
	
	/* */
	typedef structure {
		StringField f;
	} StructureField;



};


