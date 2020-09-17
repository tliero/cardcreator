package de.tilman.cardcreator;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import org.apache.hc.core5.http.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.itextpdf.barcodes.BarcodeQRCode;
import com.itextpdf.barcodes.qrcode.EncodeHintType;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.property.TextAlignment;
import com.itextpdf.layout.property.UnitValue;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.credentials.ClientCredentials;
import com.wrapper.spotify.model_objects.specification.Album;
import com.wrapper.spotify.model_objects.specification.Playlist;
import com.wrapper.spotify.model_objects.specification.Track;
import com.wrapper.spotify.requests.authorization.client_credentials.ClientCredentialsRequest;

public class CardCreator {
	
	private final static Logger log = LoggerFactory.getLogger(CardCreator.class);
	
	public float pageWidth = 297;
	public float pageHeight = 210;
	public int cardsPerPage = 6;
	public float cardWidth = 40;
	public float borderWidth = 1;
	public float qrSize = 22;
	public float topMargin = 11;
	public float coverSize = 30;
	public float artistPaddingTop = 5;
	public float artistFontSize = 8;
	public float titlePaddingTop = 3;
	public float titleFontSize = 9;
	public float titleMaxHeight = 22;

	
	public static final float mmUnit = 72f/25.4f; // see https://itextpdf.com/en/resources/faq/technical-support/itext-7/how-define-width-cell

	public static final PdfNumber INVERTEDPORTRAIT = new PdfNumber(180);
	public static final PdfNumber LANDSCAPE = new PdfNumber(90);
	public static final PdfNumber PORTRAIT = new PdfNumber(0);
	public static final PdfNumber SEASCAPE = new PdfNumber(270);
	
	private Properties properties;
	
	
	private SpotifyApi spotifyApi;
	private ClientCredentialsRequest clientCredentialsRequest;
	private Image spotifyLogo;

	public CardCreator() {
		
		List<Card> cards;
		
		try {
			// get properties
			Reader reader = new FileReader("config.properties");
			properties = new Properties();
			properties.load(reader);
			
			List<String> links = Files.readAllLines(new File(properties.getProperty("cardsFile")).toPath(), Charset.forName("UTF-8"));
			
			for (int i = links.size()-1; i >= 0; i--)
				if (links.get(i).startsWith("//") || links.get(i).length() == 0)
					links.remove(links.get(i));
				else
					if (links.get(i).startsWith("spotify:") && links.get(i).indexOf(' ') > 0)
						links.set(i, links.get(i).substring(0, links.get(i).indexOf(' ')));
			log.info("Read " + links.size() + " links from " + properties.getProperty("cardsFile"));
			
			if (properties.getProperty("spotifyClientId") != null && properties.getProperty("spotifyClientId").length() > 0) {
				spotifyApi = new SpotifyApi.Builder().setClientId(properties.getProperty("spotifyClientId")).setClientSecret(properties.getProperty("spotifyClientSecret")).build();
				clientCredentialsRequest = spotifyApi.clientCredentials().build();
				final ClientCredentials clientCredentials = clientCredentialsRequest.execute();
				
				// Set access token for further "spotifyApi" object usage
				String accessToken = clientCredentials.getAccessToken();
				log.info(accessToken);
				spotifyApi.setAccessToken(accessToken);
			}
			
			pageWidth = Integer.parseInt(properties.getProperty("pageWidth"));
			pageHeight = Integer.parseInt(properties.getProperty("pageHeight"));
			cardsPerPage = Integer.parseInt(properties.getProperty("cardsPerPage"));
			cardWidth = Integer.parseInt(properties.getProperty("cardWidth"));
			borderWidth = Integer.parseInt(properties.getProperty("borderWidth"));
			qrSize = Integer.parseInt(properties.getProperty("qrSize"));
			topMargin = Integer.parseInt(properties.getProperty("topMargin"));
			coverSize = Integer.parseInt(properties.getProperty("coverSize"));
			artistPaddingTop = Integer.parseInt(properties.getProperty("artistPaddingTop"));
			artistFontSize = Integer.parseInt(properties.getProperty("artistFontSize"));
			titlePaddingTop = Integer.parseInt(properties.getProperty("titlePaddingTop"));
			titleFontSize = Integer.parseInt(properties.getProperty("titleFontSize"));
			titleMaxHeight = Integer.parseInt(properties.getProperty("titleMaxHeight"));

			cards = fetchCardData(links);
			
			// TODO check for folders in path
			//File file = new File(DEST);
			//file.getParentFile().mkdirs();
			
			spotifyLogo = new Image(ImageDataFactory.create("resources/Spotify_Icon_RGB_Green.png"));
			spotifyLogo.setHeight(6 * mmUnit);
			
			createFoldingPdf(properties.getProperty("destinationFile"), sortCards(cards));
			
		} catch (IOException | SpotifyWebApiException e) {
			log.error(e.getMessage());
			e.printStackTrace();
		} catch (Exception e) {
			log.error(e.getMessage());
			e.printStackTrace();
		}
	}

	protected List<Card> fetchCardData(List<String> links) throws SpotifyWebApiException, IOException, ParseException {
		List<Card> cards = new LinkedList<Card>();
		HashMap<EncodeHintType, Object> hints = new HashMap<EncodeHintType, Object>();
		hints.put(EncodeHintType.CHARACTER_SET, "ISO-8859-15");

		for (String link : links) {
			log.info(link);
			
			Card card = new Card(link);
			cards.add(card);
			
			if (link.startsWith("spotify:album:")) {
				String spotifyId = link.substring(14);
				Album album = spotifyApi.getAlbum(spotifyId).build().execute();
				card.title = album.getName();
				card.artist = album.getArtists()[0].getName();

				URL url = new URL(album.getImages()[0].getUrl());
				card.cover = new Image(ImageDataFactory.create(url));
			}
			else if (link.startsWith("spotify:track:") ) {
				String spotifyId = link.substring(14);
				Track track = spotifyApi.getTrack(spotifyId).build().execute();
				card.title = track.getName();
				card.artist = track.getArtists()[0].getName();
				URL url = new URL(track.getAlbum().getImages()[0].getUrl());
				card.cover = new Image(ImageDataFactory.create(url));
			}
			else if (link.startsWith("spotify:playlist:") ) {
				String spotifyId = link.substring(17);
				Playlist playlist = spotifyApi.getPlaylist(spotifyId).build().execute();
				card.title = playlist.getName();
				card.artist = playlist.getDescription();
				URL url = new URL(spotifyApi.getPlaylistCoverImage(spotifyId).build().execute()[0].getUrl());
				card.cover = new Image(ImageDataFactory.create(url));
			}
			else {
				String coverPath = null;
				
				// extract image
				if (link.indexOf('|') > 0) {
					coverPath = link.substring(0, link.indexOf('|'));
					if (coverPath.startsWith("http://") || coverPath.startsWith("https://")) {
						card.cover = new Image(ImageDataFactory.create(coverPath));
					}
					else {
						File f = new File(coverPath);
						if (f.exists()) {
							card.cover = new Image(ImageDataFactory.create(coverPath));
						}
						else {
							log.warn("Could not find file " + coverPath);
						}
					}
					
					link = link.substring(link.lastIndexOf('|')+1);
				}
				
				if (link.startsWith("http://") || link.startsWith("https://")) {
					card.title = link;
					card.artist = "";
				}
				else {
					
					// FIXME dirty cover retrieval hack
					if (card.cover == null) {
						String path = properties.getProperty("sharePath") + link.substring(link.indexOf("/", 4)) + "/cover.jpg";
						log.debug(path);
						
						File f = new File(path);
						if (f.exists()) {
							card.cover = new Image(ImageDataFactory.create(path));
						}
					}
					
					card.title = link.substring(link.lastIndexOf('/') + 1);
					card.artist = "";
				}
				
				
			}
			
			card.qrcode = new BarcodeQRCode(link);
			card.qrcode.setHints(hints);
		}
		
		return cards;
	}

	private List<List<Card>> sortCards(List<Card> cards) {
		List<List<Card>> lines = new LinkedList<List<Card>>();
		
		List<Card> line = new LinkedList<Card>();
		
		for (Card card : cards) {
			if (line.size() < cardsPerPage) {
				line.add(card);
			}
			else {
				lines.add(line);
				line = new LinkedList<Card>();
				line.add(card);
			}
		}
		
		if (line.size() > 0)
			lines.add(line);
		
		return lines;
	}

	protected void createFoldingPdf(String dest, List<List<Card>> lines) throws Exception {
		PdfDocument pdfDoc = new PdfDocument(new PdfWriter(dest));
		pdfDoc.setDefaultPageSize(new PageSize(new Rectangle(pageWidth * mmUnit, pageHeight * mmUnit)));
		Document doc = new Document(pdfDoc);
		
		float docMargin = (pageWidth - (cardWidth * cardsPerPage)) / 2 * mmUnit;
		doc.setMargins(0, docMargin, 0,  docMargin - borderWidth);  // leave space for the first border
		
		UnitValue[] colWidths = new UnitValue[cardsPerPage];
		for (int i = 0; i < colWidths.length; i++)
			colWidths[i] = new UnitValue(UnitValue.PERCENT, 100/cardsPerPage);
		
		for (List<Card> line : lines) {
			
			Table table = new Table(colWidths);
			table.useAllAvailableWidth();
			
			Table table2 = new Table(colWidths);
			table2.useAllAvailableWidth();
			
			for (Card card : line) {
				
				Cell cell = new Cell();
				cell.setBorder(new SolidBorder(ColorConstants.GRAY, borderWidth, 0.5f));
				cell.setPadding(0);
				
				Cell cell2 = new Cell();
				cell2.setBorder(new SolidBorder(ColorConstants.GRAY, borderWidth, 0.5f));
				cell2.setPadding(0);
				
				if (card != null) {
					
					Paragraph p = new Paragraph();
					p.setTextAlignment(TextAlignment.CENTER);
					
					if (card.link.startsWith("spotify:")) {
						Paragraph logoBox = new Paragraph();
						logoBox.setTextAlignment(TextAlignment.CENTER);
						logoBox.add(spotifyLogo);
						logoBox.setMargin(3 * mmUnit);
						logoBox.setPaddingBottom(-12 * mmUnit);
						cell.add(logoBox);
						cell2.add(logoBox);
					}
					
					Image qrcode = new Image(card.qrcode.createFormXObject(pdfDoc));
					qrcode.setWidth(qrSize * mmUnit); // Cannot set margin to 0 on QR codes and they grow with the length of the content. This is an approximation.
					p.setPaddingTop(topMargin * mmUnit); // mid point of QR code is 22 mm from top border
					p.add(qrcode);
					cell.add(p);
					cell2.add(p);
					
					p = new Paragraph();
					p.setTextAlignment(TextAlignment.CENTER);
					p.setPaddingTop(5 * mmUnit);
					if (card.cover != null) {
						Image cover = card.cover;
						cover.setWidth(coverSize * mmUnit);
						p.add(cover);
					}
					else {
						p.setPaddingBottom(coverSize * mmUnit);
					}
					cell.add(p);
					cell2.add(p);
					
					p = new Paragraph();
					p.setTextAlignment(TextAlignment.CENTER);
					p.setPaddingTop(artistPaddingTop * mmUnit);
					p.setFontSize(artistFontSize);
					p.add(card.artist);
					cell.add(p);
					cell2.add(p);
					
					p = new Paragraph();
					p.setTextAlignment(TextAlignment.CENTER);
					p.setPaddingTop(titlePaddingTop * mmUnit);
					p.setFontSize(titleFontSize);
					p.setBold();
					p.add(card.title);
					p.setMaxHeight(titleMaxHeight * mmUnit);
					cell.add(p);
					cell2.add(p);
				}
				
				cell.setWidth((cardWidth * mmUnit) - borderWidth);
				table.addCell(cell.setRotationAngle(Math.PI));
				table2.addCell(cell2);
			}
			
			table.setMinHeight(pageHeight/2 * mmUnit);
			table.setMaxHeight(pageHeight/2 * mmUnit);
			doc.add(table);
			
			table2.setMinHeight(pageHeight/2 * mmUnit);
			table2.setMaxHeight(pageHeight/2 * mmUnit);
			doc.add(table2);
		}

		doc.close();
	}
	

	public static void main(String[] args) throws Exception {
		new CardCreator();
	}
}

class Card {
	String title;
	String artist;
	BarcodeQRCode qrcode;
	Image cover;
	String link;

	public Card(String link) {
		this.link = link;
	}
}