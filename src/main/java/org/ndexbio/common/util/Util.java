/**
 * Copyright (c) 2013, 2016, The Regents of the University of California, The Cytoscape Consortium
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */
package org.ndexbio.common.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import org.ndexbio.model.object.NdexPropertyValuePair;
import org.ndexbio.model.object.network.NetworkSummary;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.utils.IOUtils;

public class Util {
	public static String readFile(String path) 
			  throws IOException 
			{
			  byte[] encoded = Files.readAllBytes(Paths.get(path));
			  return new String(encoded, Charset.forName("UTF-8"));
			}
	
	public static int getNetworkScores(List<NdexPropertyValuePair> properties, boolean includeNames) {
		int i = 0;
		int j = 0;
		for (NdexPropertyValuePair prop : properties) {
			String value = prop.getValue();
			if (value != null && value.replaceAll("(\\s|\\n)", "").length()>0 ) {
				switch (prop.getPredicateString()) {
				case "name":
				case "description":
				case "version":
					if (includeNames)
						i += 10;
					break;
				case "reference":
				case "organism":
				case "disease":
				case "author":
				case "networkType":
					i += 10;
					break;
				case "tissue":
				case "labels":
				case "rights":
				case "rightsHolder":
					i += 5;
					break;
				default:
					if (j <= 15) {
						i++;
						j++;
					}
				}
			}
		}
		return i;
	}

   	
 	public static int getNdexScoreFromSummary (NetworkSummary summary) {
   		int i = 0;
   		
		if ( summary.getName() !=null && summary.getName().length()>1) {
			i += 10;
		}
		
		if (summary.getDescription() !=null && summary.getDescription().length()>1) {
			i += 10;
		}
		
		if ( summary.getVersion() !=null && summary.getVersion().length()>1) {
			i +=10;
		}
		return i + getNetworkScores(summary.getProperties(), false);
   	}
 	
 	
 	  
	public static void asyncCompressGZIP(String fileName) {
		new Thread(() -> {
			try (OutputStream fo = Files.newOutputStream(Paths.get(fileName + ".gz"));
					OutputStream gzo = new GzipCompressorOutputStream(fo)) {
				try (InputStream i = Files.newInputStream(Paths.get(fileName))) {
					IOUtils.copy(i, gzo);
					File f = new File(fileName);
					f.delete();
				}
			} catch (IOException e) {
				System.out.println(
						"ERROR: Failed to compress archived CX file " + fileName + ". Cause: " + e.getMessage());
				e.printStackTrace();
			}

		}).start();
	}
}
