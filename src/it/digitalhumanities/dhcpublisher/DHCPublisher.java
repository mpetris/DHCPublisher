/*
 * Copyright (c) 2015 Marco Petris
 * License: see LICENSE file
 */
package it.digitalhumanities.dhcpublisher;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.IOUtils;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.tool.xml.Pipeline;
import com.itextpdf.tool.xml.XMLWorker;
import com.itextpdf.tool.xml.XMLWorkerHelper;
import com.itextpdf.tool.xml.html.Tags;
import com.itextpdf.tool.xml.parser.XMLParser;
import com.itextpdf.tool.xml.pipeline.css.CSSResolver;
import com.itextpdf.tool.xml.pipeline.css.CssResolverPipeline;
import com.itextpdf.tool.xml.pipeline.end.PdfWriterPipeline;
import com.itextpdf.tool.xml.pipeline.html.AbstractImageProvider;
import com.itextpdf.tool.xml.pipeline.html.HtmlPipeline;
import com.itextpdf.tool.xml.pipeline.html.HtmlPipelineContext;

/**
 * A publishing tool for DHC file collections.
 * 
 * 
 * @author marco.petris@web.de
 *
 */
public class DHCPublisher {
	
	public DHCPublisher() {
	}

	private void convert(String unzippedDirName) {
		File unzippedDir = new File(unzippedDirName);
		File[] subDirs = unzippedDir.listFiles(new FileFilter() {
			
			@Override
			public boolean accept(File pathname) {
				return pathname.isDirectory();
			}
		});
		
		for (File subDir : subDirs) {
			System.out.println("Processing directory " + subDir + "...");
			try {
				convertFile(subDir);
				System.out.println("Conversion in " + subDir + " done.");
			}
			catch (Exception e) {
				e.printStackTrace();
				System.out.println("Could not convert HTML in " + subDir);
			}
		}
	}
	
	private void convertFile(final File subDir) throws DocumentException, IOException {
		
		File[] htmlFiles = subDir.listFiles(new FilenameFilter() {
			
			@Override
			public boolean accept(File dir, String name) {
				return name.endsWith(".html");
			}
		});
		
		if (htmlFiles.length > 0) {
			File htmlFile = htmlFiles[0];
			String targetName = htmlFile.getName().substring(0, htmlFile.getName().length()-4) + "pdf";
			
			Document document = new Document();
			File targetFile = new File(subDir, targetName);
			if (targetFile.exists()) {
				targetFile.delete();
			}
			
			try (FileOutputStream fos = new FileOutputStream(targetFile)) {
				PdfWriter writer = PdfWriter.getInstance(document, fos);
				writer.getAcroForm().setNeedAppearances(true);
				document.open();
				
				
				HtmlPipelineContext htmlContext = new HtmlPipelineContext(null);
				htmlContext.setTagFactory(Tags.getHtmlTagProcessorFactory());
				htmlContext.setImageProvider(new AbstractImageProvider() {
				    public String getImageRootPath() {
				        return subDir.getAbsolutePath();
				    }
				});

				CSSResolver cssResolver =
				    XMLWorkerHelper.getInstance().getDefaultCssResolver(true);

				Pipeline<?> pipeline =
				    new CssResolverPipeline(cssResolver,
				            new HtmlPipeline(htmlContext,
				                new PdfWriterPipeline(document, writer)));

				XMLWorker worker = new XMLWorker(pipeline, true);
				XMLParser p = new XMLParser(worker);

				try (FileInputStream fis = new FileInputStream(htmlFile)) {
					p.parse(fis);
				}
				finally {
					document.close();
				}
			}
		}
		else {
			throw new IllegalArgumentException(subDir + " does not contain HTML files!");
		}
	}

	private void unzip(String dhcDirName, String targetDirName) throws IOException {
		File dhcDir = new File(dhcDirName);
		if (!dhcDir.exists()) {
			throw new IllegalArgumentException(dhcDirName + " does not exist!");
		}
		
		File targetDir = new File(targetDirName);
		if (!targetDir.exists()) {
			targetDir.mkdirs();
		}
		
		File[] dhcFiles = dhcDir.listFiles(new FileFilter() {
			
			@Override
			public boolean accept(File pathname) {
				return pathname.isFile();
			}
		}); 
		int counter = 0;
		for (File file : dhcFiles) {
			counter++;
			System.out.println("Unzipping " + counter + ". file " + file + "...");
			try {
				unzipFile(counter, file, targetDir);
				System.out.println("Unzip for " + counter + ". file " + file + " done.");
			}
			catch (Exception e) {
				e.printStackTrace();
				System.out.println("unable to unzip " + file);
			}
		}
	}

	private void unzipFile(int counter, File file, File targetDir) throws IOException {

		String subDirName = counter + "_" + file.getName().substring(0, file.getName().length()-4);
		if (subDirName.length() > 60) {
			subDirName = subDirName.substring(0, 60);
		};
		
		File subDir = new File(targetDir, subDirName);
		if (!subDir.exists()) {
			subDir.mkdirs();
		}
		
		try (ZipInputStream zis = new ZipInputStream(new FileInputStream(file))) {
			
			ZipEntry ze = zis.getNextEntry();
			
			while(ze != null) {
				File targetFile = new File(subDir, ze.getName());
				targetFile.getParentFile().mkdirs();
				
				try (FileOutputStream fos = new FileOutputStream(targetFile)) {
					IOUtils.copy(zis, fos);
				}
				
				ze = zis.getNextEntry();
			}
		}
	}

	private static void usage() { 
		System.out.println("Usage");
		System.out.println("=====");
		System.out.println("Unzip: java -jar DHCPublisher.jar 1 dhcDir targetDir");
		System.out.println("PDF: java -jar DHCPublisher.jar 2 dirWithUnzippedDhcs");
		System.out.println("Unzip+PDF: java -jar DHCPublisher.jar 3 dhcDir targetDir");	
	}
	public static void main(String[] args) {
		
		if (args.length == 0) {
			usage();
		}
		
		try {
			Integer action = Integer.valueOf(args[0]);
			DHCPublisher dhcPublisher = new DHCPublisher();
			switch (action) {
			case 1: {
				if (args.length == 3) {
					dhcPublisher.unzip(args[1], args[2]);
				}
				else {
					usage();
				}
				break;
			}
			case 2: {
				if (args.length == 2) {
					dhcPublisher.convert(args[1]);
				}
				else {
					usage();
				}
				break;				
			}
			case 3: {
				if (args.length == 3) {
					dhcPublisher.unzip(args[1], args[2]);
					dhcPublisher.convert(args[2]);
				}
				else {
					usage();
				}
				break;				
			}
			default: {
				usage();
			}
			}
		} catch (Exception e) {
			e.printStackTrace();
			usage();
		}
	}
}
