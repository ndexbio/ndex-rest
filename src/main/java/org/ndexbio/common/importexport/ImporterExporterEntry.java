package org.ndexbio.common.importexport;

import java.util.List;

public class ImporterExporterEntry {

	private String name;
	private String description;
	private String fileExtension;
	private String directoryName;
	private List<String> exporterCmd;
	private List<String> importerCmd;
	
	public ImporterExporterEntry() {}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getFileExtension() {
		return fileExtension;
	}

	public void setFileExtension(String fileExtension) {
		this.fileExtension = fileExtension;
	}

	public String getDirectoryName() {
		return directoryName;
	}

	public void setDirectoryName(String directoryName) {
		this.directoryName = directoryName;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public List<String> getExporterCmd() {
		return exporterCmd;
	}

	public void setExporterCmd(List<String> exporterCmd) {
		this.exporterCmd = exporterCmd;
	}

	public List<String> getImporterCmd() {
		return importerCmd;
	}

	public void setImporterCmd(List<String> importerCmd) {
		this.importerCmd = importerCmd;
	}
	
}
