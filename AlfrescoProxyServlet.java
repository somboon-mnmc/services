package com.skytizens.joget.pma;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.ByteArrayPartSource;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.io.IOUtils;
import org.joget.commons.util.FileLimitException;
import org.joget.commons.util.FileManager;
import org.joget.commons.util.FileStore;
import org.joget.commons.util.SetupManager;
import org.joget.plugin.base.ExtDefaultPlugin;
import org.joget.plugin.base.PluginWebSupport;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

public class AlfrescoProxyServlet extends ExtDefaultPlugin implements PluginWebSupport {

	private static final String STRING_HOST_HEADER_NAME = "Host";
	private static final String STRING_ORIGIN_HEADER_NAME = "Origin";
	private static final String STRING_REFERER_HEADER_NAME = "Referer";
	private static final String STRING_CONTENT_LENGTH_HEADER_NAME = "Content-Length";
	private static final String STRING_LOCATION_HEADER = "Location";
	private static final int FOUR_KB = 4196;
	private static final File FILE_UPLOAD_TEMP_DIRECTORY = new File(System.getProperty("java.io.tmpdir"));
	private static final String STRING_CONTENT_TYPE_HEADER_NAME = "Content-Type";
	private boolean followRedirects;
	private int intMaxFileUploadSize = 5 * 1024 * 1024;
	private Properties properties;
	private static final String[] IGNORED_PARAMETER = {"pluginName","service","api","res","jw"};
	//private static final String[] IGNORED_PARAMETER = {"pluginName","service","res","jw"};
	
	@Override
	public String getName() {
		return "Alfresco Proxy";
	}

	@Override
	public String getDescription() {
		return "Skytizens proxy allow execute webservices on Alfresco.";
	}

	@Override
	public String getVersion() {
		return Constants.VERSION;
	}

	protected static String getPropertiesPath() {
		return SetupManager.getBaseSharedDirectory() + "alfresco.properties";
	}

	@Override
	public void webService(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		Map map = request.getParameterMap();
		properties = new Properties();
		FileInputStream fis = new FileInputStream(new File(getPropertiesPath()));
		properties.load(fis);

		String service = request.getParameter("service");
		String api = request.getParameter("api");
		String res = request.getParameter("res");
		String jw = request.getParameter("jw");
		String context = null;
		if (service != null && !service.isEmpty()) {
			context = "/alfresco/service/" + service;
		} else if (api != null && !api.isEmpty()) {
			context = "/alfresco/api/" + api;
		} else if (res != null && !res.isEmpty()) {
			context = "/share/res/" + res;	
		} else if (jw != null && !jw.isEmpty()) {
			context = "/jw/" + jw;
		}
		if (context != null) {
			String destURL = getDestURL(context);
			
//			String query = request.getQueryString();
//			System.out.println(query);
//			int ind = query.indexOf("&");
//			if(ind > 0) {
//				destURL += query.substring(ind);
//			}
			
//			System.out.println("URL : : : >" + destURL);
			
			if (request.getMethod().equalsIgnoreCase("GET")) {
				doGet(request, response, destURL);
			} else if (request.getMethod().equalsIgnoreCase("POST")) {
//				if (service.contains("api/jogetupload") || service.contains("slingshot/profile/uploadsignature")) {// "api/jogetupload"
//					doUpload(request, response, destURL);
//				} else if (service.contains("slingshot/profile/uploadavatar")
//						|| service.contains("slingshot/profile/dsigning/certsignature")
//						|| service.contains("slingshot/profile/dsigning/uploaddsignature")) {// "api/jogetupload"
//					doUploadAvatar(request, response, destURL);
//				} else if (service.contains("api/digitalSigning/certificate")
//						|| service.contains("dsigning/signfile")) {
//					doUploadDigitalSignature(request, response, destURL);
//				} else {
				doPost(request, response, destURL);
//				}
			}
		}
	}

	private void doGet(HttpServletRequest request, HttpServletResponse response, String destURL) {
		try {
			GetMethod getMethodRequest = new GetMethod(destURL);

			setProxyRequestHeaders(request, getMethodRequest, destURL);

			this.executeProxyRequest(getMethodRequest, request, response, false);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void doUpload(HttpServletRequest request, HttpServletResponse response, String destUrl)
			throws IOException, ServletException {
		try {
			PostMethod postMethodProxyRequest = new PostMethod(destUrl);
			// handle multipart files
			MultipartFile file = FileStore.getFile("filedata");
			String path = FileManager.storeFile(file);
			if (path != null) {
				try {
					File f = FileManager.getFileByPath(path);
					Part[] parts = { new FilePart("filedata", f),
							new StringPart("filename", URLEncoder.encode(f.getName(), "UTF-8")),
							new StringPart("destination", request.getParameter("destination")),
							new StringPart("uploaddirectory", request.getParameter("uploaddirectory")),
							new StringPart("createdirectory", request.getParameter("createdirectory")),
							new StringPart("majorVersion", request.getParameter("majorVersion")),
							new StringPart("username", request.getParameter("username")),
							new StringPart("overwrite", request.getParameter("overwrite")),
							new StringPart("thumbnails", request.getParameter("thumbnails")),
							new StringPart("updatenameandmimetype", request.getParameter("updatenameandmimetype")),
							new StringPart("uploadprops", request.getParameter("uploadprops")),
							new StringPart("doctype", request.getParameter("doctype"))
							// new StringPart("updatenameandmimetype", request.getParameter("overwrite")),
							// new StringPart("uploadprops",request.getParameter("uploadprops").equals("")?
							// "":request.getParameter("uploadprops"), "UTF-8"),//a1;cm:description,test
							// title;cm:title
							// new StringPart("doctype",request.getParameter("doctype").equals("")?
							// "":request.getParameter("doctype"))
							// new StringPart("siteId", request.getParameter("overwrite")),
							// new StringPart("containerId", request.getParameter("overwrite")),
					};

					postMethodProxyRequest
							.setRequestEntity(new MultipartRequestEntity(parts, postMethodProxyRequest.getParams()));

					executeProxyRequest(postMethodProxyRequest, request, response, true);

				} finally {
					FileManager.deleteFileByPath(path);
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			FileStore.clear();
		}
	}

	public void doUploadAvatar(HttpServletRequest request, HttpServletResponse response, String destUrl)
			throws IOException, ServletException {
		try {
			PostMethod postMethodProxyRequest = new PostMethod(destUrl);
			// handle multipart files
			MultipartFile file = FileStore.getFile("filedata");
			String path = FileManager.storeFile(file);
			if (path != null) {
				try {
					File f = FileManager.getFileByPath(path);
					Part[] parts = { new FilePart("filedata", f),
							new StringPart("filename", URLEncoder.encode(f.getName(), "UTF-8")),
							new StringPart("destination", request.getParameter("destination")),
							new StringPart("uploaddirectory", request.getParameter("uploaddirectory")),
							new StringPart("createdirectory", request.getParameter("createdirectory")),
							new StringPart("majorVersion", request.getParameter("majorVersion")),
							new StringPart("username", request.getParameter("username")),
							new StringPart("overwrite", request.getParameter("overwrite")),
							new StringPart("thumbnails", request.getParameter("thumbnails")),
							new StringPart("updatenameandmimetype", request.getParameter("updatenameandmimetype")),
							new StringPart("siteId", request.getParameter("siteId")),
							new StringPart("containerId", request.getParameter("containerId")) };

					postMethodProxyRequest
							.setRequestEntity(new MultipartRequestEntity(parts, postMethodProxyRequest.getParams()));

					executeProxyRequest(postMethodProxyRequest, request, response, true);

				} finally {
					FileManager.deleteFileByPath(path);
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			FileStore.clear();
		}
	}

	public void doUploadDigitalSignature(HttpServletRequest request, HttpServletResponse response, String destUrl)
			throws IOException, ServletException {
		try {
			PostMethod postMethodProxyRequest = new PostMethod(destUrl);
			// handle multipart files
			MultipartFile file = FileStore.getFile("filedata");
			String path = FileManager.storeFile(file);
			if (path != null) {
				try {
					File f = FileManager.getFileByPath(path);
					Part[] parts = { new StringPart(
							"template_x002e_user-dsignature_x002e_sky-features_x0023_default-configCertDialog-parentNodeRef",
							request.getParameter(
									"template_x002e_user-dsignature_x002e_sky-features_x0023_default-configCertDialog-parentNodeRef")),
							new StringPart("storetype", request.getParameter("storetype")),
							new StringPart("password", request.getParameter("password")),
							new StringPart("repassword", request.getParameter("repassword")),
							new StringPart("alias", request.getParameter("alias")),
							new StringPart("keytype", request.getParameter("keytype")),
							new StringPart("algorithm", request.getParameter("algorithm")),
							new StringPart("keysize", request.getParameter("keysize")),
							new StringPart("validity", request.getParameter("validity")),
							new StringPart("alert", request.getParameter("alert")),
							new StringPart("cn", request.getParameter("cn")),
							new StringPart("title", request.getParameter("title")),
							new StringPart("organization", request.getParameter("organization")),
							new StringPart("email", request.getParameter("email")),
							new StringPart("ou", request.getParameter("ou")),
							new StringPart("location", request.getParameter("location")),
							new StringPart("state", request.getParameter("state")),
							new StringPart("country", request.getParameter("country")) };

					postMethodProxyRequest
							.setRequestEntity(new MultipartRequestEntity(parts, postMethodProxyRequest.getParams()));

					executeProxyRequest(postMethodProxyRequest, request, response, true);

				} finally {
					FileManager.deleteFileByPath(path);
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			FileStore.clear();
		}
	}

	public void doPost(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, String destinationUrl) throws IOException, ServletException {
	    // Retrieve content type of the incoming request
	    String contentType = httpServletRequest.getContentType();
	    // Initialize POST request for the destination URL
	    PostMethod postMethodProxyRequest = new PostMethod(destinationUrl);
	    
	    // Handle multipart form data
	    if (ServletFileUpload.isMultipartContent(httpServletRequest)) {
//	        try {
				if (FileStore.getFile("filedata") != null) {
					System.out.println("1");
				    this.handleMultipartPost2(postMethodProxyRequest, httpServletRequest, httpServletResponse);
				} else {
					System.out.println("2");
				    this.handleStandardPost(postMethodProxyRequest, httpServletRequest);
				}
//			} catch (FileLimitException | ServletException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
	    } else {
	        // Handle non-multipart form data
	    	System.out.println("3");
	        if (contentType == null || contentType.contains(PostMethod.FORM_URL_ENCODED_CONTENT_TYPE)) {
	        	System.out.println("4");
	        	this.handleStandardPost(postMethodProxyRequest, httpServletRequest);
	        } else {
	        	System.out.println("5");
	            this.handleContentPost(postMethodProxyRequest, httpServletRequest);
	        }
	        
	        this.executeProxyRequest(postMethodProxyRequest, httpServletRequest, httpServletResponse, false);
	    }

	    // Execute the proxy request
//	    this.executeProxyRequest(postMethodProxyRequest, httpServletRequest, httpServletResponse, false);
	}


	public void handleMultipartPost2(PostMethod postMethodProxyRequest, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException {
		
		try {
	        
			// handle multipart files
			MultipartFile file = FileStore.getFile("filedata");
			String path = FileManager.storeFile(file);
			
			System.out.println("path:" + path);
			
			if (path != null) {
				try {
					File f = FileManager.getFileByPath(path);
					Map map = httpServletRequest.getParameterMap();
					Set<String> keys = map.keySet();
					Iterator<String> iterator = keys.iterator();
					List<Part> list = new ArrayList<Part>();
					if (f.length() > 0) {
						list.add(new FilePart("filedata", f));
						list.add(new StringPart("filename", URLEncoder.encode(f.getName(), "UTF-8")));
					}
					
					List<String> ignored = Arrays.asList(IGNORED_PARAMETER);

					while (iterator.hasNext()) {
						String key = iterator.next();
						if (!ignored.contains(key)) {
							list.add(new StringPart(key, httpServletRequest.getParameter(key)));
						}
					}
					Part[] parts = new Part[list.size()];
					int i = 0;
					for (Part part : list)
						parts[i++] = part;
					
					
					
					postMethodProxyRequest
							.setRequestEntity(new MultipartRequestEntity(parts, postMethodProxyRequest.getParams()));
					this.executeProxyRequest(postMethodProxyRequest, httpServletRequest, httpServletResponse, false);
				} finally {
					FileManager.deleteFileByPath(path);
				}
			} else {
				String path2 = "workspace://SpacesStore/2064662c-ad3b-4aed-8031-41a1960c71f9";
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			FileStore.clear();	
		}

	}

	private void setProxyRequestHeaders(HttpServletRequest httpServletRequest, HttpMethod httpMethodProxyRequest,
			String destUrl) {
		boolean hasReferer = false;
		Enumeration enumerationOfHeaderNames = httpServletRequest.getHeaderNames();
		while (enumerationOfHeaderNames.hasMoreElements()) {
			String stringHeaderName = (String) enumerationOfHeaderNames.nextElement();
			if (stringHeaderName.equalsIgnoreCase(STRING_CONTENT_LENGTH_HEADER_NAME)) {
				continue;
			}

			Enumeration enumerationOfHeaderValues = httpServletRequest.getHeaders(stringHeaderName);
			while (enumerationOfHeaderValues.hasMoreElements()) {
				String stringHeaderValue = (String) enumerationOfHeaderValues.nextElement();
				if (stringHeaderName.equalsIgnoreCase(STRING_HOST_HEADER_NAME)) {
					stringHeaderValue = getHostAndPort();
				}
				if (stringHeaderName.equalsIgnoreCase(STRING_REFERER_HEADER_NAME)) {
					stringHeaderValue = destUrl;
					hasReferer = true;
				}
				if (stringHeaderName.equalsIgnoreCase(STRING_ORIGIN_HEADER_NAME)) {
					stringHeaderValue = getBaseURL();
				}

				Header header = new Header(stringHeaderName, stringHeaderValue);
				// Set the same header on the proxy request
				httpMethodProxyRequest.setRequestHeader(header);
			}

		}
	}

	private void executeProxyRequest(HttpMethod httpMethodProxyRequest, HttpServletRequest httpServletRequest,
			HttpServletResponse httpServletResponse, boolean isUpload) throws IOException, ServletException {
		if (properties.getProperty("protocol").equalsIgnoreCase("https")) {
			Protocol.registerProtocol("https", new Protocol("https", new EasySslProtocolSocketFactory(),
					Integer.valueOf(properties.getProperty("port"))));
		}
		// Create a default HttpClient
		HttpClient httpClient = new HttpClient();
		// httpClient.getParams().setAuthenticationPreemptive(true);
		httpClient.getHttpConnectionManager().getParams().setConnectionTimeout(30000);
		httpClient.getHttpConnectionManager().getParams().setSoTimeout(30000);
		httpClient.getState().setCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, "Alfresco"),
				new UsernamePasswordCredentials(properties.getProperty("user"), properties.getProperty("password")));

		httpMethodProxyRequest.setFollowRedirects(false);
		httpMethodProxyRequest.setDoAuthentication(true);

		// set query string params
		if (httpServletRequest.getQueryString().startsWith("service=components/control/finder-upload"))
			httpMethodProxyRequest.setQueryString(httpServletRequest.getQueryString());

		// Execute the request
		int intProxyResponseCode = httpClient.executeMethod(httpMethodProxyRequest);

		InputStream is = httpMethodProxyRequest.getResponseBodyAsStream();

		// Pass the response code back to the client
		httpServletResponse.setStatus(intProxyResponseCode);

		// Pass response headers back to the client
		Header[] headerArrayResponse = httpMethodProxyRequest.getResponseHeaders();
		
		//HERE Authentication redirect
		for (Header header : headerArrayResponse) {
			if (header.getName().equals("Transfer-Encoding") && header.getValue().equals("chunked")
					|| header.getName().equals("Content-Encoding") && header.getValue().equals("gzip") || // don't copy
																											// gzip																							// header
					header.getName().equals("WWW-Authenticate") || // don't copy WWW-Authenticate header so browser
																  // doesn't prompt on failed basic auth
					header.getName().equals("Set-Cookie")) { // Cookie force user logout 
				// proxy servlet does not support chunked encoding
			} else {
				httpServletResponse.setHeader(header.getName(), header.getValue());
			}
		}

		try {
			if (intProxyResponseCode == 200) {
				OutputStream out = httpServletResponse.getOutputStream();
				// httpServletResponse.setCharacterEncoding("UTF-8");
				IOUtils.copy(is, out);
				out.flush();
				out.close();
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private boolean isBodyParameterGzipped(List<Header> responseHeaders) {
		for (Header header : responseHeaders) {
			if (header.getValue().equals("gzip")) {
				return true;
			}
		}
		return false;
	}

	private byte[] ungzip(final byte[] gzipped) throws IOException {
		final GZIPInputStream inputStream = new GZIPInputStream(new ByteArrayInputStream(gzipped));
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(gzipped.length);
		final byte[] buffer = new byte[FOUR_KB];
		int bytesRead = 0;
		while (bytesRead != -1) {
			bytesRead = inputStream.read(buffer, 0, FOUR_KB);
			if (bytesRead != -1) {
				byteArrayOutputStream.write(buffer, 0, bytesRead);
			}
		}
		byte[] ungzipped = byteArrayOutputStream.toByteArray();
		inputStream.close();
		byteArrayOutputStream.close();
		return ungzipped;
	}

	@SuppressWarnings("unchecked")
	private void handleMultipartPost(PostMethod postMethodProxyRequest, HttpServletRequest httpServletRequest)
			throws ServletException {
		// Create a factory for disk-based file items
		DiskFileItemFactory diskFileItemFactory = new DiskFileItemFactory();
		// Set factory constraints
		// diskFileItemFactory.setSizeThreshold(this.getMaxFileUploadSize());
		diskFileItemFactory.setRepository(FILE_UPLOAD_TEMP_DIRECTORY);
		// Create a new file upload handler
		ServletFileUpload servletFileUpload = new ServletFileUpload(diskFileItemFactory);
		// Parse the request
		try {
			// Get the multipart items as a list
			List<FileItem> listFileItems = (List<FileItem>) servletFileUpload.parseRequest(httpServletRequest);
			// Create a list to hold all of the parts
			List<org.apache.commons.httpclient.methods.multipart.Part> listParts = new ArrayList<org.apache.commons.httpclient.methods.multipart.Part>();
			// Iterate the multipart items list
			// Collection<Part> parts = httpServletRequest.getParts();
			for (FileItem fileItemCurrent : listFileItems) {
				// If the current item is a form field, then create a string part
				if (fileItemCurrent.isFormField()) {
					StringPart stringPart = new StringPart(fileItemCurrent.getFieldName(), // The field name
							fileItemCurrent.getString() // The field value
					);
					// Add the part to the list
					listParts.add(stringPart);
				} else {
					// The item is a file upload, so we create a FilePart
					FilePart filePart = new FilePart(fileItemCurrent.getFieldName(), // The field name
							new ByteArrayPartSource(fileItemCurrent.getName(), // The uploaded file name
									fileItemCurrent.get() // The uploaded file contents
							));
					// Add the part to the list
					listParts.add(filePart);
				}
			}
			MultipartRequestEntity multipartRequestEntity = new MultipartRequestEntity(
					listParts.toArray(new org.apache.commons.httpclient.methods.multipart.Part[] {}),
					postMethodProxyRequest.getParams());
			postMethodProxyRequest.setRequestEntity(multipartRequestEntity);
			// The current content-type header (received from the client) IS of
			// type "multipart/form-data", but the content-type header also
			// contains the chunk boundary string of the chunks. Currently, this
			// header is using the boundary of the client request, since we
			// blindly copied all headers from the client request to the proxy
			// request. However, we are creating a new request with a new chunk
			// boundary string, so it is necessary that we re-set the
			// content-type string to reflect the new chunk boundary string
			postMethodProxyRequest.setRequestHeader(STRING_CONTENT_TYPE_HEADER_NAME,
					multipartRequestEntity.getContentType());
		} catch (Exception fileUploadException) {
			throw new ServletException(fileUploadException);
		}
	}

	@SuppressWarnings("unchecked")
	private void handleStandardPost(PostMethod postMethodProxyRequest, HttpServletRequest httpServletRequest) {
	    // Get the client POST data as a Map
	    Map<String, String[]> mapPostParameters = (Map<String, String[]>) httpServletRequest.getParameterMap();
	    
	    // Create a List to hold the NameValuePairs to be passed to the PostMethod
	    List<NameValuePair> listNameValuePairs = new ArrayList<NameValuePair>();
	    
	    // List of ignored parameters
	    List<String> ignored = Arrays.asList(IGNORED_PARAMETER);
	    
	    // Iterate the parameter names
	    for (String stringParameterName : mapPostParameters.keySet()) {
	        if (!ignored.contains(stringParameterName)) {
	            // Iterate the values for each parameter name
	            String[] stringArrayParameterValues = mapPostParameters.get(stringParameterName);
	            for (String stringParameterValue : stringArrayParameterValues) {
	                // Create a NameValuePair and store in list
	                NameValuePair nameValuePair = new NameValuePair(stringParameterName, stringParameterValue);
	                listNameValuePairs.add(nameValuePair);
	            }
	        }
	    }
	    
	    // Set the proxy request POST data
	    postMethodProxyRequest.setRequestBody(listNameValuePairs.toArray(new NameValuePair[] {}));
	}


	private void handleContentPost(PostMethod postMethodProxyRequest, HttpServletRequest httpServletRequest)
			throws IOException, ServletException {
		InputStream in = httpServletRequest.getInputStream();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] content = new byte[2048];
		int bytesRead = -1;
		while ((bytesRead = in.read(content)) != -1) {
			baos.write(content, 0, bytesRead);
		}
		in.close();

		String contentType = httpServletRequest.getContentType();

		ByteArrayRequestEntity entity;
		// StringRequestEntity entity;
		try {
			entity = new ByteArrayRequestEntity(baos.toByteArray(), contentType);
		} catch (Exception e) {
			throw new ServletException(e);
		}
		// Set the proxy request POST data
		postMethodProxyRequest.setRequestEntity(entity);
		postMethodProxyRequest.setRequestHeader(STRING_CONTENT_TYPE_HEADER_NAME, contentType);

		baos.close();
	}

	private String getDestURL(String context) {
		try {
			String ret = "";
			for(var i = 0; i < context.length(); i++ ) {
				char char1= context.charAt(i);
				String char2 = String.valueOf(context.charAt(i));  
				
				if((char1 >= '0'  && char1 <= '9') || (char1 >= 'a' && char1<= 'z') || (char1 >= 'A' && char1 <= 'Z') || char1 == '/' || char1 == '?' || char1== '&' || char1 == '=' || char1 == '-'){
					ret += char1;
				}else {
					ret += URLEncoder.encode(char2, StandardCharsets.UTF_8.toString());
				}
			}
			
			return getBaseURL() + ret.replace(" ", "%20");
		}catch(Exception e) {
			e.printStackTrace();
		}
		return null;		
	}

	private String getHostAndPort() {
		return properties.getProperty("host") + ":" + properties.getProperty("port");
	}

	private String getBaseURL() {
		return properties.getProperty("protocol") + "://" + properties.getProperty("host") + ":"
				+ properties.getProperty("port");
	}

	protected int getMaxFileUploadSize() {
		return this.intMaxFileUploadSize;
	}
}
