/*******************************************************************************
 * @author Reika Kalseki
 * 
 * Copyright 2017
 * 
 * All rights reserved.
 * Distribution of the software in any form is only allowed with
 * explicit, prior permission from the owner.
 ******************************************************************************/
package Reika.DragonAPI.IO;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Serializable;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.jar.JarFile;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import Reika.DragonAPI.DragonAPICore;
import Reika.DragonAPI.Libraries.Java.ReikaJVMParser;
import Reika.DragonAPI.Libraries.Java.ReikaJavaLibrary;
import Reika.DragonAPI.Libraries.Java.ReikaObfuscationHelper;
import Reika.DragonAPI.Libraries.Java.ReikaStringParser;

public class ReikaFileReader extends DragonAPICore {

	private static long internetLastUnavailable = -1;

	public static int getFileLength(File f) {
		int len;
		try {
			LineNumberReader lnr = new LineNumberReader(new FileReader(f));
			lnr.skip(Long.MAX_VALUE);
			len = lnr.getLineNumber()+1+1;
			lnr.close();
		}
		catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Could not load file data due to "+e.getCause()+" and "+e.getClass()+" !");
		}
		return len;
	}

	public static BufferedReader getReader(File f) {
		try {
			return new BufferedReader(new InputStreamReader(new FileInputStream(f)));
		}
		catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public static BufferedReader getReader(InputStream in) {
		try {
			return new BufferedReader(new InputStreamReader(in));
		}
		catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public static BufferedReader getReader(String path) {
		try {
			return new BufferedReader(new InputStreamReader(new FileInputStream(path)));
		}
		catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public static BufferedReader getReader(URL url, int timeout, ConnectionErrorHandler ch, DataFetcher f) {
		if (!isInternetAccessible(timeout)) {
			if (ch != null)
				ch.onNoInternet();
			return null;
		}

		try {
			URLConnection c = url.openConnection();
			c.setConnectTimeout(timeout);
			if (f != null) {
				try {
					f.fetchData(c);
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			}
			return new BufferedReader(new InputStreamReader(c.getInputStream()));
		}
		catch (UnknownHostException e) { //Server not found
			if (ch != null)
				ch.onServerNotFound();
		}
		catch (ConnectException e) { //Redirect/tampering
			if (ch != null)
				ch.onServerRedirected();
		}
		catch (SocketTimeoutException e) { //Slow internet, cannot load a text file...
			if (ch != null)
				ch.onTimedOut();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	private static boolean isInternetAccessible(int timeout) {
		if (ReikaObfuscationHelper.isDeObfEnvironment() && ReikaJVMParser.isArgumentPresent("-DragonAPI_NoInternet")) {
			DragonAPICore.log("Internet is marked inaccessible.");
			return false;
		}
		int dt = 60*1000*(ReikaObfuscationHelper.isDeObfEnvironment() ? 5 : 1);
		if (internetLastUnavailable+dt >= System.currentTimeMillis()) //only check at most once a minute (five for dev)
			return false;
		String[] attempts = {
				"http://www.google.com",
				"http://en.wikipedia.org/wiki/Main_Page",
				"http://github.com/",
				"http://msdn.microsoft.com/en-us/default.aspx",
				"https://aws.amazon.com/",
				"ns1.telstra.net"
		};
		for (int i = 0; i < attempts.length; i++) {
			try {
				URLConnection c = new URL(attempts[i]).openConnection();
				c.setConnectTimeout(timeout);
				((HttpURLConnection)c).getResponseCode();
				return true;
			}
			catch (IOException ex) {

			}
		}
		internetLastUnavailable = System.currentTimeMillis();
		return false;
	}

	/** Gets all files with the given extension in a directory and any subdirectories. */
	public static ArrayList<File> getAllFilesInFolder(File f, String... ext) {
		ArrayList<File> li = new ArrayList();
		if (f.isDirectory()) {
			File[] files = f.listFiles();
			for (int i = 0; i < files.length; i++) {
				File in = files[i];
				if (in.isDirectory()) {
					li.addAll(getAllFilesInFolder(in, ext));
				}
				else {
					if (ext == null) {
						li.add(in);
					}
					else {
						for (int k = 0; k < ext.length; k++) {
							if (in.getName().endsWith(ext[k])) {
								li.add(in);
							}
						}
					}
				}
			}
		}
		return li;
	}

	/** Gets all files in a directory and any subdirectories. */
	public static ArrayList<File> getAllFilesInFolder(File f) {
		return getAllFilesInFolder(f, null);
	}

	public static String readTextFile(Class root, String path) {
		InputStream in = root.getResourceAsStream(path);
		StringBuilder sb = new StringBuilder();
		BufferedReader p;
		try {
			p = new BufferedReader(new InputStreamReader(in));
		}
		catch (NullPointerException e) {
			DragonAPICore.logError("File "+path+" does not exist!");
			return sb.toString();
		}
		int i = 0;
		try {
			String line = null;
			while((line = p.readLine()) != null) {
				if (!line.isEmpty()) {
					sb.append(line);
					i++;
					sb.append("\n");
				}
			}
			p.close();
		}
		catch (Exception e) {
			DragonAPICore.logError(e.getMessage()+" on loading line "+i);
		}
		return sb.toString();
	}

	public static ArrayList<String> getFileAsLines(String path, boolean printStackTrace) {
		return getFileAsLines(getReader(path), printStackTrace);
	}

	public static ArrayList<String> getFileAsLines(URL url, int timeout, boolean printStackTrace, ConnectionErrorHandler ch) {
		return getFileAsLines(url, timeout, printStackTrace, ch, null);
	}

	public static ArrayList<String> getFileAsLines(URL url, int timeout, boolean printStackTrace, ConnectionErrorHandler ch, DataFetcher f) {
		BufferedReader r = getReader(url, timeout, ch, f);
		return r != null ? getFileAsLines(r, printStackTrace) : null;
	}

	public static ArrayList<String> getFileAsLines(File f, boolean printStackTrace) {
		return getFileAsLines(getReader(f), printStackTrace);
	}

	public static ArrayList<String> getFileAsLines(InputStream in, boolean printStackTrace) {
		return getFileAsLines(getReader(in), printStackTrace);
	}

	public static ArrayList<String> getFileAsLines(BufferedReader r, boolean printStackTrace) {
		ArrayList<String> li = new ArrayList();
		String line = "";
		try {
			while (line != null) {
				line = r.readLine();
				if (line != null) {
					li.add(line);
				}
			}
			r.close();
		}
		catch (Exception e) {
			if (printStackTrace)
				e.printStackTrace();
		}
		return li;
	}

	public static ArrayList<Byte> getFileAsBytes(InputStream in, boolean printStackTrace) {
		BufferedReader r = getReader(in);
		ArrayList<Byte> li = new ArrayList();
		try {
			byte b = (byte)r.read();
			while (b != -1) {
				li.add(b);
				b = (byte)r.read();
			}
			r.close();
		}
		catch (Exception e) {
			if (printStackTrace)
				e.printStackTrace();
		}
		return li;
	}

	public static BufferedWriter getWriter(File f) {
		try {
			return new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f)));
		}
		catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public static void writeLinesToFile(String s, ArrayList<String> li, boolean printStackTrace) {
		writeLinesToFile(new File(s), li, printStackTrace);
	}

	public static void writeLinesToFile(File f, ArrayList<String> li, boolean printStackTrace) {
		try {
			writeLinesToFile(new BufferedWriter(new PrintWriter(f)), li, printStackTrace);
		}
		catch (IOException e) {
			if (printStackTrace) {
				e.printStackTrace();
			}
		}
	}

	public static void writeLinesToFile(BufferedWriter p, ArrayList<String> li, boolean printStackTrace) {
		String sep = System.getProperty("line.separator");
		try {
			for (String s : li) {
				p.write(s+sep);
			}
			p.flush();
			p.close();
		}
		catch (IOException e) {
			if (printStackTrace) {
				e.printStackTrace();
			}
		}
	}

	public static void writeDataToFile(File f, ArrayList<Byte> li, boolean printStackTrace) {
		try {
			f.delete();
			f.getParentFile().mkdirs();
			f.createNewFile();
			FileOutputStream fos = new FileOutputStream(f);
			BufferedWriter p = new BufferedWriter(new OutputStreamWriter(fos));
			for (byte b : li) {
				p.write(b);
			}
			p.flush();
			p.close();
		}
		catch (IOException e) {
			if (printStackTrace) {
				e.printStackTrace();
			}
		}
	}

	public static InputStream convertLinesToStream(ArrayList<String> li, boolean printStackTrace) {
		String sep = System.getProperty("line.separator");
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(bos));
		try {
			for (String s : li)
				writer.write(s+sep);
			writer.close();
		}
		catch (IOException e) {
			if (printStackTrace) {
				e.printStackTrace();
			}
		}
		byte[] bytes = bos.toByteArray();
		return new ByteArrayInputStream(bytes);
	}

	public static String getHash(String path, HashType type) {
		return getHash(new File(path), type);
	}

	public static String getHash(File file, HashType type) {
		try {
			return getHash(new FileInputStream(file), type);
		}
		catch (Exception e) {
			e.printStackTrace();
			return "";
		}
	}

	public static String getHash(InputStream is, HashType type) {
		StringBuffer sb = new StringBuffer();
		try {
			byte[] buffer = new byte[1024];
			MessageDigest complete = MessageDigest.getInstance(type.tag);
			int numRead;

			do {
				numRead = is.read(buffer);
				if (numRead > 0)
					complete.update(buffer, 0, numRead);
			}
			while (numRead != -1);

			is.close();
			byte[] hash = complete.digest();

			for (int i = 0; i < hash.length; i++) {
				sb.append(Integer.toString((hash[i] & 0xff) + 0x100, 16).substring(1).toUpperCase());
			}
		}
		catch (Exception e) {
			e.printStackTrace();
			sb.append("IO ERROR: ");
			sb.append(e.toString());
		}
		return sb.toString();
	}

	public static interface ConnectionErrorHandler {

		void onServerRedirected();
		void onTimedOut();
		void onNoInternet();
		void onServerNotFound();

	}

	public static interface DataFetcher {

		void fetchData(URLConnection c) throws Exception;

	}

	public static enum HashType {
		MD5("MD5"),
		SHA1("SHA-1"),
		SHA256("SHA-256");

		private final String tag;

		private HashType(String s) {
			tag = s;
		}

		public String hash(Object o) {
			return this.hashBytes(this.getBytes(o));
		}

		private String hashBytes(byte[] bytes) {
			try {
				MessageDigest messageDigest = MessageDigest.getInstance(tag);
				messageDigest.update(bytes);
				return new String(messageDigest.digest(), StandardCharsets.UTF_8);
			}
			catch (NoSuchAlgorithmException e) {
				return null; //never happens
			}
		}

		private byte[] getBytes(Object o) {
			if (o instanceof byte[])
				return (byte[])o;
			if (o instanceof Integer)
				return ReikaJavaLibrary.splitIntToHexChars((int)o);
			if (o instanceof String)
				return ((String)o).getBytes();
			if (o instanceof Serializable) {
				try {
					ByteArrayOutputStream buf = new ByteArrayOutputStream();
					ObjectOutputStream oo = new ObjectOutputStream(buf);
					oo.writeObject(o);
					return buf.toByteArray();
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			}
			DragonAPICore.logError("Cannot serialize an object "+o+" of type "+o.getClass()+"!");
			return new byte[0]; //unserializable
		}
	}

	/** Edits individual lines matching in a file if they match a given criterion. */
	public static abstract class LineEditor {

		/** Attempt line editing? */
		public abstract boolean editLine(String s);

		/** The line used to replace strings that match the criteria. Args: Original line, newline separator */
		protected abstract String getReplacementLine(String s, String newline);

		public final boolean performChanges(File f) {
			try {
				BufferedReader r = new BufferedReader(new FileReader(f));
				String sep = System.getProperty("line.separator");
				String line = r.readLine();
				StringBuilder out = new StringBuilder();
				while (line != null) {
					String rep = this.editLine(line) ? this.getReplacementLine(line, sep) : line;
					if (rep == null) {

					}
					else {
						out.append(rep+sep);
					}
					line = r.readLine();
				}
				r.close();
				FileOutputStream os = new FileOutputStream(f);
				os.write(out.toString().getBytes());
				os.close();
				return true;
			}
			catch (IOException e) {
				e.printStackTrace();
				return false;
			}
		}

	}

	public static InputStream getFileInsideJar(File f, String name) {
		try {
			return getFileInsideJar(new JarFile(f), name);
		}
		catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static InputStream getFileInsideJar(JarFile jar, String name) {
		try {
			return jar.getInputStream(jar.getEntry(name));
		}
		catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static boolean deleteFolderWithContents(File f) {
		return deleteFolderWithContents(f, 10);
	}

	public static boolean deleteFolderWithContents(File f, int tries) {
		Exception e = null;
		for (int i = 0; i < tries; i++) {
			try {
				FileUtils.forceDelete(f);
				return true;
			}
			catch (Exception ex) {
				e = ex;
			}
		}
		if (e != null) {
			e.printStackTrace();
		}
		return false;
	}

	public static void copyFile(InputStream in, OutputStream out, int size) throws FileReadException, FileWriteException {
		copyFile(in, out, size, null);
	}

	public static void copyFile(InputStream in, OutputStream out, int chunkSize, WriteCallback call) throws FileReadException, FileWriteException {
		byte[] bytes = new byte[chunkSize];
		int count = 0;
		while (count != -1) {
			if (count > 0) {
				try {
					out.write(bytes, 0, count);
					if (call != null)
						call.onWrite(bytes);
				}
				catch (IOException e) {
					throw new FileWriteException(e);
				}
			}
			try {
				count = in.read(bytes, 0, bytes.length);
			}
			catch (IOException e) {
				throw new FileReadException(e);
			}
		}
	}

	public static class FileReadException extends IOException {

		private FileReadException(IOException e) {
			super(e);
		}

	}

	public static class FileWriteException extends IOException {

		private FileWriteException(IOException e) {
			super(e);
		}

	}

	public static interface WriteCallback {

		public void onWrite(byte[] data);

	}

	public static File createFileFromStream(InputStream in) throws IOException {
		File tempFile = File.createTempFile("temp_"+String.valueOf(in.hashCode()), null);
		tempFile.deleteOnExit();
		FileOutputStream out = new FileOutputStream(tempFile);
		IOUtils.copy(in, out);
		return tempFile;
	}

	public static void writeUncompressedNBT(NBTTagCompound tag, File f) throws IOException {
		writeUncompressedNBT(tag, new FileOutputStream(f));
	}

	public static void writeUncompressedNBT(NBTTagCompound tag, OutputStream out) throws IOException {
		CompressedStreamTools.write(tag, new DataOutputStream(out));
	}

	public static NBTTagCompound readUncompressedNBT(File f) throws IOException {
		return readUncompressedNBT(new FileInputStream(f));
	}

	public static NBTTagCompound readUncompressedNBT(InputStream in) throws IOException {
		return CompressedStreamTools.func_152456_a(new DataInputStream(in), NBTSizeTracker.field_152451_a);
	}

	public static boolean isEmpty(File f) throws IOException {
		BufferedReader br = new BufferedReader(new FileReader(f));
		String line = br.readLine();
		if (line == null || (line.length() == 0 && br.readLine() == null)) {
			br.close();
			return true;
		}
		else {
			br.close();
			return false;
		}
	}

	public static void emptyDirectory(File dir) {
		File[] f = dir.listFiles();
		if (f == null)
			return;
		for (int i = 0; i < f.length; i++) {
			f[i].delete();
		}
	}

	public static void clearFile(File f) {
		try {
			f.delete();
			f.createNewFile();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void flipFileBytes(InputStream in, File out) {
		try {
			ArrayList<Byte> li = new ArrayList();
			byte b = (byte)in.read();
			while (b != -1) {
				li.add(b);
				b = (byte)in.read();
			}
			Collections.reverse(li);
			writeDataToFile(out, li, true);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static ArrayList<String> encryptFileBytes(InputStream in) {
		try {
			ArrayList<Byte> li = new ArrayList();
			byte b = (byte)in.read();
			while (b != -1) {
				li.add(b);
				b = (byte)in.read();
			}
			encryptByteList(li);
			ArrayList<String> li2 = new ArrayList();
			String line = "";
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < li.size(); i++) {
				byte b2 = li.get(i);
				int val = Math.abs(b2);
				String s = String.valueOf(val);
				if (val < 10)
					s = "0"+s;
				if (val < 100)
					s = "0"+s;
				if (b2 < 0)
					s = "-"+s;
				else
					s = "0"+s;
				sb.append(s);
				if (i%56 == 55 || i == li.size()-1) {
					li2.add(sb.toString());
					sb = new StringBuilder();
				}
			}
			return li2;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static void encryptByteList(ArrayList<Byte> li) {
		Collections.reverse(li);
		for (int i = li.size()-1; i >= 0; i--) {
			byte get = li.get(i);
			get = ReikaJavaLibrary.flipBits(get);
			get = (byte)(~get);
			li.set(i, get);
			li.add(i+1, (byte)rand.nextInt(255));
		}
	}

	public static ArrayList<Byte> decryptFileBytes(InputStream in) {
		return decryptByteList(getFileAsLines(in, true));
	}

	public static ArrayList<Byte> decryptByteList(ArrayList<String> li2) {
		ArrayList<Byte> li = new ArrayList();
		for (String s : li2) {
			List<String> arr = ReikaStringParser.splitStringByLength(s, 4);
			for (String s2 : arr) {
				byte b = Byte.parseByte(s2);
				li.add(b);
			}
		}
		Iterator<Byte> it = li.iterator();
		int idx = 0;
		while (it.hasNext()) {
			byte b2 = it.next();
			if (idx%2 == 1)
				it.remove();
			idx++;
		}
		for (int i = 0; i < li.size(); i++) {
			byte get = li.get(i);
			get = (byte)(~get);
			get = ReikaJavaLibrary.flipBits(get);
			li.set(i, get);
		}
		Collections.reverse(li);
		return li;
	}

	public static InputStream decryptInputStream(InputStream in)  {
		ArrayList<Byte> data = decryptFileBytes(in);
		ByteArrayOutputStream bin = new ByteArrayOutputStream();
		for (byte b : data)
			bin.write(b);
		return new ByteArrayInputStream(bin.toByteArray());
	}
}
