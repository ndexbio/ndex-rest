package org.ndexbio.xbel.splitter;

import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import org.ndexbio.xbel.cache.XbelCacheService;

import org.ndexbio.xbel.model.Parameter;
import org.ndexbio.xbel.model.Statement;
import org.ndexbio.xbel.model.StatementGroup;
import org.ndexbio.xbel.model.Subject;
import org.ndexbio.xbel.model.Term;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

public class StatementGroupSplitter extends XBelSplitter {
	private static final String xmlElement = "statementGroup";
	private static Joiner idJoiner = Joiner.on(":").skipNulls();

	public StatementGroupSplitter(JAXBContext context) {
		super(context, xmlElement);
		// TODO Auto-generated constructor stub
	}

	@Override
	protected void process() throws JAXBException {
		// instantiate outer level StatementGroup
		StatementGroup sg = (StatementGroup) unmarshallerHandler.getResult();
		this.processStatementGroup(sg);

	}

	private void processStatementGroup(StatementGroup sg) {
		// process the Statemenst belonging to this Statement Group
		this.processStatements(sg);
		// process any embedded StatementGroup(s)
		for (StatementGroup isg : sg.getStatementGroup()) {
			this.processStatementGroup(isg);
		}
	}

	/*
	 * process statement group
	 */
	private void processStatements(StatementGroup sg) {
		List<Statement> statementList = sg.getStatement();
		for (Statement statement : statementList) {
			//System.out.println("Processing Statement: "
			//		+ statement.getRelationship().toString());
			this.processStatementSubject(statement.getSubject());
			this.processObject(statement.getObject());
		}
	}

	private void processStatementSubject(Subject sub) {
		if (null == sub) {
			return;
		}
		try {
			this.processOuterTerm(sub.getTerm());
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void processObject(org.ndexbio.xbel.model.Object obj) {
		if (null == obj) {
			return;
		}
		try {
			if( null != obj.getStatement()){
				System.out.println("Object has internal statement " 
						+obj.getStatement().getRelationship().toString());
			} else {
				this.processOuterTerm(obj.getTerm());
			}
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void processOuterTerm(Term term) throws ExecutionException {
		
		List<Long> jdexIdList = Lists.newArrayList(); // list of child term ids
		this.processInnerTerms(term, jdexIdList);
		this.createBaseTermForFunctionTerm(term, jdexIdList);
		//TODO persist to database
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
	private void processInnerTerms(Term term, List<Long> childList)
			throws ExecutionException {

		for (Object o : term.getParameterOrTerm()) {
			if (o instanceof Term) {
				
				List<Long> innerList = Lists.newArrayList();
				
				processInnerTerms((Term) o, innerList); 
				/*
				 * find or create a BaseTerm for this FunctionTerm's function
				 */
				this.createBaseTermForFunctionTerm(term, innerList);
				// obtain a new or existing JDEX ID from the cache based on the
				// identifier
				Long termId = XbelCacheService.INSTANCE.accessTermCache().get(
						idJoiner.join(innerList));
				//TODO persist to database

				if (null != childList) {
					childList.add(termId);
				}

			} else {
				Parameter p = (Parameter) o;
				Long termId = XbelCacheService.INSTANCE.accessTermCache().get(
						idJoiner.join(p.getNs(), p.getValue()));
				//TODO persist to database
				childList.add(termId);
			}
		}
	}

	/*
	 * The function portion of a FunctionTerm is also a BaseTerm and needs to be
	 * included as a child term in the function term
	 */
	private void createBaseTermForFunctionTerm(Term term, List<Long> idList)
			throws ExecutionException {
		Parameter p = new Parameter();
		p.setNs("BEL");
		p.setValue(term.getFunction().value());
		
		Long termId = XbelCacheService.INSTANCE.accessTermCache().get(
				idJoiner.join(p.getNs(), p.getValue()));
		//TODO: persist to database
		idList.add(termId);

	}


}
