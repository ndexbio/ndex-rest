package org.ndexbio.xbel.splitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import org.ndexbio.orientdb.service.NDExPersistenceService;
import org.ndexbio.orientdb.service.NDExPersistenceServiceFactory;
import org.ndexbio.orientdb.service.XBelNetworkService;
import org.ndexbio.rest.domain.IBaseTerm;
import org.ndexbio.rest.domain.ICitation;
import org.ndexbio.rest.domain.IFunctionTerm;
import org.ndexbio.rest.domain.INode;
import org.ndexbio.rest.domain.ISupport;
import org.ndexbio.rest.domain.ITerm;
import org.ndexbio.xbel.cache.XbelCacheService;
import org.ndexbio.xbel.model.AnnotationGroup;
import org.ndexbio.xbel.model.Citation;
import org.ndexbio.xbel.model.Parameter;
import org.ndexbio.xbel.model.Statement;
import org.ndexbio.xbel.model.StatementGroup;
import org.ndexbio.xbel.model.Subject;
import org.ndexbio.xbel.model.Term;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class StatementGroupSplitter extends XBelSplitter {
	private static final String xmlElement = "statementGroup";
	private static Joiner idJoiner = Joiner.on(":").skipNulls();
	private NDExPersistenceService persistenceService;

	public StatementGroupSplitter(JAXBContext context) {
		super(context, xmlElement);
		/*
		 * this class persists XBEL data into orientdb establish a reference to
		 * the persistence service
		 */
		this.persistenceService = NDExPersistenceServiceFactory.INSTANCE
				.getNDExPersistenceService();
	}

	@Override
	protected void process() throws JAXBException, ExecutionException {
		// instantiate outer level StatementGroup
		StatementGroup sg = (StatementGroup) unmarshallerHandler.getResult();
		this.processStatementGroup(sg);

	}

	private void processStatementGroup(StatementGroup sg)
			throws ExecutionException {
		processStatementGroup(sg, null, null);
	}

	// In an XBEL document, only one Citation and one Support are in scope for
	// any Statement
	// Therefore, as we recursively process StatementGroups, if the
	// AnnotationGroup for the inner StatementGroup
	// contains a Citation, it overrides any Citation set in the outer
	// StatementGroup. And the same
	// is true for Supports.
	private void processStatementGroup(StatementGroup sg,
			ISupport outerSupport, ICitation outerCitation)
			throws ExecutionException {
		// process the Annotation group for this Statement Group
		AnnotationGroup annotationGroup = sg.getAnnotationGroup();

		ICitation citation = citationFromAnnotationGroup(annotationGroup);
		if (citation != null) {
			// The AnnotationGroup had a Citation. This overrides the
			// outerCitation.
			// Furthermore, this means that the outerSupport does NOT apply to
			// the inner StatementGroup
			// The Support will either be null or will be specified in the
			// AnnotationGroup
			outerSupport = null;
		} else {
			// There was no Citation in the AnnotationGroup, so use the
			// outerCitation
			citation = outerCitation;
		}

		// The ICitation is passed to the supportFromAnnotationGroup method
		// because
		// any ISupport created will be in the context of the ICitation and
		// should be linked to it.
		ISupport support = supportFromAnnotationGroup(annotationGroup, citation);
		if (support == null) {
			// The AnnotationGroup had no Support, therefore use the
			// outerSupport
			support = outerSupport;
		}

		// process the Statements belonging to this Statement Group
		this.processStatements(sg, support, citation);
		// process any embedded StatementGroup(s)
		for (StatementGroup isg : sg.getStatementGroup()) {
			this.processStatementGroup(isg, support, citation);
		}
	}

	private ISupport supportFromAnnotationGroup(
			AnnotationGroup annotationGroup, ICitation citation)
			throws ExecutionException {
		for (Object object : annotationGroup
				.getAnnotationOrEvidenceOrCitation()) {
			if (object instanceof String) {
				// No explicit type for Evidence, therefore if it is a string,
				// its an Evidence and we find/create an ISupport
				return XBelNetworkService.getInstance().findOrCreateISupport(
						(String) object, citation);
			}
		}
		return null;
	}

	private ICitation citationFromAnnotationGroup(
			AnnotationGroup annotationGroup) throws ExecutionException {
		for (Object object : annotationGroup
				.getAnnotationOrEvidenceOrCitation()) {
			if (object instanceof Citation) {
				return XBelNetworkService.getInstance().findOrCreateICitation(
						(Citation) object);
			}
		}
		return null;
	}

	/*
	 * process statement group
	 */
	private void processStatements(StatementGroup sg, ISupport support,
			ICitation citation) throws ExecutionException {
		List<Statement> statementList = sg.getStatement();
		for (Statement statement : statementList) {
			// System.out.println("Processing Statement: "
			// + statement.getRelationship().toString());
			IBaseTerm predicate = XBelNetworkService.getInstance()
					.findOrCreatePredicate(statement.getRelationship());
			INode subjectNode = this.processStatementSubject(statement
					.getSubject());
			INode objectNode = this.processStatementObject(statement
					.getObject());
			XBelNetworkService.getInstance().createIEdge(subjectNode, objectNode, predicate, support, citation);
		}
	}

	private INode processStatementSubject(Subject sub) {
		if (null == sub) {
			return null;
		}
		try {
			IFunctionTerm representedTerm = this
					.processOuterTerm(sub.getTerm());
			INode subjectNode = XBelNetworkService.getInstance()
					.findOrCreateINodeForIFunctionTerm(representedTerm);
			return subjectNode;
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	private INode processStatementObject(org.ndexbio.xbel.model.Object obj) {
		if (null == obj) {
			return null;
		}
		try {
			if (null != obj.getStatement()) {
				System.out.println("Object has internal statement "
						+ obj.getStatement().getRelationship().toString());
			} else {
				IFunctionTerm representedTerm = this.processOuterTerm(obj
						.getTerm());
				INode objectNode = XBelNetworkService.getInstance()
						.findOrCreateINodeForIFunctionTerm(representedTerm);
				return objectNode;
			}
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	private IFunctionTerm processOuterTerm(Term term) throws ExecutionException {

		List<Long> jdexIdList = Lists.newArrayList(); // list of child term ids
		this.processInnerTerms(term, jdexIdList);
		Long jdexId = XbelCacheService.INSTANCE.accessTermCache().get(
				idJoiner.join(jdexIdList));
		return persistFunctionTerm(term, jdexId, jdexIdList);
	}

	/*
	 * A XBel Term model object implements a quasi-composite pattern in that it
	 * contains a List<Object> that my contain either Term or Pattern model
	 * objects. A XBel Term object is equivalent to a NDEx FunctionTerm object
	 * while a XBel Parameter object is equivalent to a NDEx BaseTerm object.
	 * 
	 * This method works through the parent/child hierarchy of the outermost
	 * Term found in a found in a XBel Subject or (XBel) Object object. It
	 * utilizes the XBelCacheService to distinguish novel from existing
	 * FunctionTerms and BaseTerms. It maintains an identifier for each
	 * FunctionTerm and BaseTerm. For FunctionTerms this is a concatenated
	 * String of the JDex IDs of its children. For BaseTerms, it is a String
	 * conatining the namespace and term value.
	 */
	private void processInnerTerms(Term term, List<ITerm> childList)
			throws ExecutionException {

		for (Object o : term.getParameterOrTerm()) {
			if (o instanceof Term) {				
				List<ITerm> innerChildList = Lists.newArrayList();				
				processInnerTerms((Term) o, innerChildList); 			
				// obtain a new or existing JDEX ID from the cache based on the
				// identifier
				Long jdexId = XbelCacheService.INSTANCE.accessTermCache().get(
						idJoiner.join(innerChildList));
				
				IFunctionTerm ft = persistFunctionTerm( term, jdexId,  childList);
				if (null != childList) {
					childList.add(ft);
				}

			} else {
				Parameter p = (Parameter) o;
				Long jdexId = XbelCacheService.INSTANCE.accessTermCache().get(
						idJoiner.join(p.getNs(), p.getValue()));
				IBaseTerm bt = XBelNetworkService.getInstance().createIBaseTerm(p, jdexId);
					
				childList.add(bt);
			}
		}
	}
	

	  private IFunctionTerm persistFunctionTerm(Term term, Long jdexId, List<ITerm> childList) {
		  try {
			IFunctionTerm ft = persistenceService.findOrCreateIFunctionTerm(jdexId);		
			ft.setTermFunction(this.createBaseTermForFunctionTerm(term, childList));			
			Map<Integer,ITerm> ftMap  = Maps.newHashMap();			
			ft.setTermParameters(ftMap);			
			int ftCount = 0;
			for(ITerm childITerm: childList){
				ftCount++;
				ft.getTermParameters().put(ftCount, childITerm);
			}
			return ft;
			
		} catch (ExecutionException e) {
			
			e.printStackTrace();
			return null;
		}
		  
	  }
	  
	  
	/*
	 * The function portion of a FunctionTerm is also a BaseTerm and needs to be
	 * included as a child term in the function term
	 */
	private IBaseTerm createBaseTermForFunctionTerm(Term term, List<ITerm> idList)
			throws ExecutionException {
		Parameter p = new Parameter();
		p.setNs("BEL");
		p.setValue(term.getFunction().value());

		Long jdexId = XbelCacheService.INSTANCE.accessTermCache().get(
				idJoiner.join(p.getNs(), p.getValue()));
		IBaseTerm bt = XBelNetworkService.getInstance().createIBaseTerm(p, jdexId);
		idList.add(bt);
		return bt;
	}

}
