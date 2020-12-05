package org.ndexbio.rest.services.v3;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Set;

import org.ndexbio.common.persistence.CX2NetworkLoader;
import org.ndexbio.cx2.aspect.element.core.CxAttributeDeclaration;
import org.ndexbio.cx2.aspect.element.core.CxEdge;
import org.ndexbio.cx2.aspect.element.core.CxVisualProperty;
import org.ndexbio.cx2.aspect.element.core.DeclarationEntry;
import org.ndexbio.cx2.aspect.element.core.DefaultVisualProperties;
import org.ndexbio.cxio.aspects.datamodels.ATTRIBUTE_DATA_TYPE;
import org.ndexbio.cxio.aspects.datamodels.EdgeAttributesElement;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.network.query.FilterCriterion;
import org.ndexbio.rest.Configuration;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class EdgeFilter {
	
	//private String networkId ;
	private int edgeLimit;
//	private OutputStream out;
	private FilterCriterion criterion;
	
	private String aspectDir;
	ATTRIBUTE_DATA_TYPE type ;
	
	private DeclarationEntry decl;
	private ObjectMapper om;
	private ComparisonOperator operator;
		
	private boolean useAscOrder;
	
	/**
	 * 
	 * @param networkId
	 * @param criteria
	 * @param limit
	 * @param returnTopN when this parameter is true, return topN based on the filtering criteria, when it is false, 
	 * return the firstN edges.
	 * @throws IOException 
	 * @throws JsonMappingException 
	 * @throws JsonParseException 
	 * @throws NdexException 
	 */
	public EdgeFilter (String networkId, FilterCriterion criterion, int limit, String orderby) throws JsonParseException, JsonMappingException, IOException, NdexException {
		//this.networkId = networkId;
		this.edgeLimit = limit;
	//	this.out = output; 
		this.criterion = criterion;
		
		aspectDir = Configuration.getInstance().getNdexRoot() + File.separator + "data" +File.separator + networkId
				+ File.separator + CX2NetworkLoader.cx2AspectDirName+  File.separator;
		om = new ObjectMapper();
		getAttrDeclarations();

		this.type = this.decl.getDataType();

		switch (criterion.getOperator()) {
		case ">": 
			operator = new GreaterThan();
			break;
		case "<":
			operator = new LessThan();
			break;
		case "=":
			operator = new Equals();
			break;
		case "!=":
			operator = new NotEquals();
			break;
		default:
			throw new NdexException("Comparison operator " + criterion.getOperator() + " is not supported.");
			
		}
		
		if ( orderby.equalsIgnoreCase("asc"))
			useAscOrder = true;
		else if ( orderby.equalsIgnoreCase("desc"))
			useAscOrder = false;
		else
			throw new NdexException ( orderby + " is an invalid value for attribute \"orderby\"." );
		
	}
	
	public Set<CxEdge> filterTopN() throws FileNotFoundException, IOException, NdexException {
		EdgeFilterComparator cmp = new EdgeFilterComparator(criterion.getName(), decl, useAscOrder);
		TopNEdgeHolder result = new TopNEdgeHolder(edgeLimit,cmp);
		
		try (FileInputStream inputStream = new FileInputStream(aspectDir + CxEdge.ASPECT_NAME)) {

			Iterator<CxEdge> it = om.readerFor(CxEdge.class).readValues(inputStream);
			
			while (it.hasNext()) {
				CxEdge edge = it.next();
				if ( satisfied(edge)) {
					result.addEdge(edge);
				}
			}	
		}
		
		return result.getEdges();
		
	}
	
	public void filterFirstN() {
		
	}
	
	public void randomN() {
		
	}

	private void getAttrDeclarations() throws JsonParseException, JsonMappingException, IOException, NdexException {
		File vsFile = new File(aspectDir + CxAttributeDeclaration.ASPECT_NAME);
		
		CxAttributeDeclaration[] ds = om.readValue(vsFile, CxAttributeDeclaration[].class); 
		
		decl= ds[0].getDeclarations().get(CxEdge.ASPECT_NAME).get(criterion.getName());
		if ( decl == null) {
			throw new NdexException ("Attibute " + criterion.getName() + " is not declared in edges.");
		}
	}
	
    private boolean satisfied (CxEdge e) throws NdexException {
    	
    	Object v = e.getWelldoneAttributeValue(criterion.getName(), decl);
    	switch (type) {
     	case DOUBLE: {
     		Double d = (Double)v;
     		Double condValue = Double.valueOf(criterion.getValue());
     		return operator.compare(d, condValue);
     	}
     	case LONG: {
     		Long d = (Long)v;
     		Long condValue = Long.valueOf(criterion.getValue());
     		return operator.compare(d, condValue);
     	}	   
     	case INTEGER:{
     		Integer d = (Integer)v;
     		Integer condValue = Integer.valueOf(criterion.getValue());
     		return operator.compare(d, condValue);
     		   }
     	case STRING: {
     		return operator.compare((String)v, criterion.getValue());
     		}
     	case BOOLEAN:{
     		Boolean d = (Boolean)v;
     		Boolean condValue = Boolean.valueOf(criterion.getValue());
     		return operator.compare(d, condValue);
     	}
     	default: 
     		throw new NdexException ("Filtering on list values are not implemented.");
     	}
     	  
     } 

   interface ComparisonOperator{
       <T> boolean compare(Comparable<T> op1,Comparable<T> op2);
   }

   interface ValueChecker<T> {
        boolean satisfied (T op1, T op2) ;
   }

   public class GreaterThan implements ComparisonOperator{

	@Override
	public <T> boolean compare(Comparable<T> op1, Comparable<T> op2) {
		return op1.compareTo((T)op2) > 0;
	}
   }
   
   public class LessThan implements ComparisonOperator{

	@Override
	public <T> boolean compare(Comparable<T> op1, Comparable<T> op2) {
		return op1.compareTo((T)op2) < 0;
	}
   }
 
   public class Equals implements ComparisonOperator{

	@Override
	public <T> boolean compare(Comparable<T> op1, Comparable<T> op2) {
		return op1.compareTo((T)op2) == 0;
	}
   }
   
   public class NotEquals implements ComparisonOperator{

	@Override
	public <T> boolean compare(Comparable<T> op1, Comparable<T> op2) {
		return op1.compareTo((T)op2) != 0;
	}
   }

}
