package org.ndexbio.rest.services.v3;

import java.util.Comparator;
import java.util.List;

import org.ndexbio.cx2.aspect.element.core.CxEdge;
import org.ndexbio.cx2.aspect.element.core.DeclarationEntry;
import org.ndexbio.cxio.aspects.datamodels.ATTRIBUTE_DATA_TYPE;
import org.ndexbio.model.exceptions.NdexException;

public class EdgeFilterComparator implements Comparator<CxEdge> {
	
	private String attributeName;
	private DeclarationEntry edgeAttrDeclaration;
	private int modifier;
	
	public EdgeFilterComparator(String attrName, DeclarationEntry edgeAttrDecl, boolean useAscOrder) {
		this.attributeName = attrName;
		this.edgeAttrDeclaration = edgeAttrDecl;
		this.modifier = useAscOrder ? 1 : -1;
	}

	@Override
	public int compare(CxEdge o1, CxEdge o2) {
		try {
			Object v1 = o1.getWelldoneAttributeValue(attributeName, edgeAttrDeclaration);
			Object v2 = o2.getWelldoneAttributeValue(attributeName, edgeAttrDeclaration);
		
			if ( v1 == null && v2 == null )
				return 0;
			if ( v2 == null )
				return 1;
			if ( v1 == null)
				return -1;
			
			ATTRIBUTE_DATA_TYPE type = edgeAttrDeclaration.getDataType();
			if ( type.isSingleValueType())
				return compareSingleValue(type, v1,v2);
			
			List<Object> list1 = (List<Object>)v1;
			List<Object> list2 = (List<Object>) v2;
			int l2Len = list2.size();
			for ( int i = 0 ; i < list1.size(); i++ ) {
				if ( i >= l2Len)
					return 1;
				Object lv1 = list1.get(i);
				Object lv2 = list2.get(i);
				int r = compareSingleValue(type.elementType(), lv1, lv2);
				if ( r != 0)
					return r;
			}
			if ( list1.size() == l2Len)
				return 0;
			return -1;
		} catch ( NdexException e) {
			throw new RuntimeException ("NdexException thrown: " + e.getMessage());
		}
	}

	
	private int compareSingleValue(ATTRIBUTE_DATA_TYPE type, Object v1, Object v2) {
		
		int r;
		switch (type) {
		case STRING: 
			r = ((String)v1).compareTo((String)v2) ;
			break;
		case BOOLEAN:
			r= ((Boolean)v1).compareTo((Boolean)v2);
			break;
		case DOUBLE:
			r= ((Double)v1).compareTo((Double)v2);
			break;
		case INTEGER:
			r = ((Integer)v1).compareTo((Integer)v2);
			break;
		case LONG:
			r= ((Long)v1).compareTo((Long)v2);
			break;
		default:
			throw new RuntimeException(type.toString() + " is not a single data type.");
		}
		
		return r * modifier;
	}
}
