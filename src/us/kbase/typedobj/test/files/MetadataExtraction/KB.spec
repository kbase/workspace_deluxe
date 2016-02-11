/*
  Note that @searchable (some missing an @) annotations are kept in the spec to
  ensure that they do not interfere with metadata extraction and for spec
  backwards compatibility purposes.
*/
module KB {

	/*
	*/
	typedef structure {
		string data;
		int stuff;
	} NoExtractionData;
	
	/*
	  @optional size
	  @searchable ws_subset name,alias
	  @searchable ws_subset size
	  @searchable ws_subset width
	  @metadata ws name AS My Name
	  @metadata ws size AS Size
	  @metadata ws width as width
	  @metadata ws length(name) as name length
	*/
	typedef structure {
		string name;
		string alias;
		int size;
		int maxsize;
		float width;
		float maxwidth;
	} SimpleStructure;

	/*
	  @metadata ws f1
	  @metadata ws f2
	  @metadata ws f3
	  @metadata ws f4
	*/
	typedef structure {
		float f1;
		float f2;
		float f3;
		float f4;
	} FloatStructure;

	/*
	  @searchable ws_subset numbers
	  @searchable ws_subset floaters.*
	  @metadata ws length(numbers) as n numbers
	*/
	typedef structure {
		mapping<string,int> numbers;
		mapping<string,float> floaters;
		string throwAwayData;
	} MappingStruct;
	
	
	/*
	searchable ws_subset s i
	@metadata ws s As String Data
	@metadata ws i As Integer
	The line below has tabs vs. spaces for parser testing purposes.
	@metadata ws	f	as floater
	*/
	typedef structure {
		string s;
		int i;
		float f;
	} MetaDataT1;

	
	/*
	@metadata ws length(s) As StringLength
	*/
	typedef structure {
		string s;
	} MetaDataT2;
	
	
	typedef string otherstring;
	/*
	@metadata ws length(s)
	*/
	typedef structure {
		otherstring s;
	} MetaDataT3;
	
	/*
	@optional l m lm mm t
	@metadata ws length(l) As ListLength
	@metadata ws length(m) As MapLength
	@metadata ws length(lm) As ListOfMapLength
	@metadata ws length(mm) As MapOfMapLength
	@metadata ws length(t) As TupleLength
	*/
	typedef structure {
		list<string> l;
		mapping<string,int> m;
		list<mapping<string,int>> lm;
		mapping<string,mapping<string,int>> mm;
		tuple<string,mapping<string,int>,int> t;
	} MetaDataT4;


	/*
	@metadata ws t3.s AS my string
	@metadata ws length(t3.s) AS len
	*/
	typedef structure {
		MetaDataT3 t3;
		string otherthing;
	} MetaDataT5;

	/*
	@searchable ws_subset t5
	@metadata ws t5.t3.s AS my string
	*/
	typedef structure {
		MetaDataT5 t5;
	} MetaDataT6;

	/*
	@searchable ws_subset t5.otherthing
	@metadata ws t5.t3.s AS my string
	*/
	typedef structure {
		MetaDataT5 t5;
	} MetaDataT7;

	/*
	@searchable ws_subset stuff.*
	@metadata ws length(stuff) as number of things
	@metadata ws otherThing AS an int
	*/
	typedef structure {
		mapping<string,string> stuff;
		int otherThing;
	} MetaDataT8;

	/*
	@searchable ws_subset t8.stuff
	@metadata ws t8.otherThing AS my thing
	*/
	typedef structure {
		MetaDataT8 t8;
	} MetaDataT9;
};

