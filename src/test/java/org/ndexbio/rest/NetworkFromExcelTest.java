package org.ndexbio.rest;

import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.ndexbio.rest.domain.Permissions;
import org.ndexbio.rest.exceptions.NdexException;
import org.ndexbio.rest.models.BaseTerm;
import org.ndexbio.rest.models.Edge;
import org.ndexbio.rest.models.Membership;
import org.ndexbio.rest.models.Network;
import org.ndexbio.rest.models.Node;
import org.ndexbio.rest.models.SearchParameters;
import org.ndexbio.rest.models.SearchResult;
import org.ndexbio.rest.models.Term;
import org.ndexbio.rest.models.User;
import org.ndexbio.rest.services.NetworkService;
import org.ndexbio.rest.services.UserService;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;

public class NetworkFromExcelTest {

	private String excelFilePath = "/resources/exceltestnetwork.xls";
	private String testUserName = "jstegall";
	private Integer idCounter = 0;

	@Test
	public void createExcelNetwork() {
		final UserService userService = new UserService();
		SearchParameters searchParameters = new SearchParameters();
		searchParameters.setSearchString(testUserName);
		searchParameters.setSkip(0);
		searchParameters.setTop(1);

		try {
			SearchResult<User> result = userService.findUsers(searchParameters);
			User testUser = (User) result.getResults().iterator().next();
			loadExcelNetwork(testUser, excelFilePath);
		} catch (NdexException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			Assert.fail(e.getMessage());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			Assert.fail(e.getMessage());
		}
	}

	private void loadExcelNetwork(User testUser, String excelPath)
			throws Exception {

		final URL testNetworkUrl = getClass().getResource(excelPath);
		File file = new File(testNetworkUrl.toURI());
		FileInputStream networkFileStream = new FileInputStream(file);

		// Get the workbook instance for XLS file
		HSSFWorkbook workbook = new HSSFWorkbook(networkFileStream);

		// Get first sheet from the workbook
		HSSFSheet sheet = workbook.getSheetAt(0);

		// Put into the Network model format
		Network networkToCreate = networkFromWorkSheet(sheet);
		System.out.println("Creating network from excel file: "
				+ file.getName() + ".");
		List<Membership> membershipList = new ArrayList<Membership>();
		Membership membership = new Membership();
		membership.setResourceId(testUser.getId());
		membership.setResourceName(testUser.getUsername());
		membership.setPermissions(Permissions.ADMIN);
		membershipList.add(membership);
		networkToCreate.setMembers(membershipList);
		networkToCreate.setTitle(file.getName());

		final NetworkService networkService = new NetworkService();
		final Network newNetwork = networkService
				.createNetwork(networkToCreate);
		Assert.assertNotNull(newNetwork);
	}

	private Network networkFromWorkSheet(HSSFSheet sheet) {
		// Get iterator to all the rows in current sheet
		Network network = new Network();
		Iterator<Row> rowIterator = sheet.iterator();

		// The first row should have the headers, skip them.
		rowIterator.next();
		// TODO validate column names

		// We are assuming SIF 3 column format
		network.setFormat("SIF");

		// TODO add support for other columns

		Map<String, BaseTerm> termMap = new HashMap<String, BaseTerm>();
		Map<String, Node> nodeMap = new HashMap<String, Node>();

		// Iterate over the remaining rows to load each edge
		while (rowIterator.hasNext()) {
			Row row = rowIterator.next();
			String subjectIdentifier = getCellText(row.getCell(0));
			String predicateIdentifier = getCellText(row.getCell(1));
			String objectIdentifier = getCellText(row.getCell(2));

			if (!subjectIdentifier.isEmpty() && !predicateIdentifier.isEmpty()
					&& !objectIdentifier.isEmpty()) {

				BaseTerm predicate = findOrCreateBaseTerm(predicateIdentifier, termMap,
						network);
				BaseTerm subjectTerm = findOrCreateBaseTerm(subjectIdentifier, termMap,
						network);
				Node subjectNode = findOrCreateNode(subjectTerm, nodeMap,
						network);
				BaseTerm objectTerm = findOrCreateBaseTerm(objectIdentifier, termMap,
						network);
				Node objectNode = findOrCreateNode(objectTerm, nodeMap,
						network);
				createEdge(subjectNode, predicate, objectNode,
						network);
			}

		}

		return network;
	}

	private String getCellText(Cell cell) {
		switch (cell.getCellType()) {
		case Cell.CELL_TYPE_BOOLEAN:
			return Boolean.toString(cell.getBooleanCellValue());
		case Cell.CELL_TYPE_NUMERIC:
			return Double.toString(cell.getNumericCellValue());
		case Cell.CELL_TYPE_STRING:
			return cell.getStringCellValue();
		}
		return "";
	}

	private void createEdge(Node subjectNode, Term predicate, Node objectNode,
			Network network) {
		Edge edge = new Edge();
		edge.setObjectId(objectNode.getId());
		edge.setPredicateId(predicate.getId());
		edge.setSubjectId(subjectNode.getId());
		idCounter++;
		edge.setId(idCounter.toString());
		network.getEdges().put(edge.getId(), edge);
	}

	private Node findOrCreateNode(BaseTerm term, Map<String, Node> nodeMap,
			 Network network) {
		Node node = nodeMap.get(term.getName());
		if (node != null)
			return node;
		idCounter++;
		node = new Node();
		node.setId(idCounter.toString());
		node.setRepresents(term.getId());
		node.setName(term.getName());
		network.getNodes().put(node.getId(), node);
		nodeMap.put(node.getName(), node);
		return node;
	}

	private BaseTerm findOrCreateBaseTerm(String identifier, Map<String, BaseTerm> termMap,
			Network network) {
		BaseTerm term = termMap.get(identifier);
		if (term != null)
			return term;
		idCounter++;
		BaseTerm newTerm = new BaseTerm();
		newTerm.setId(idCounter.toString());
		newTerm.setName(identifier);
		network.getTerms().put(newTerm.getId(), newTerm);
		termMap.put(identifier, newTerm);
		return newTerm;
	}

}
