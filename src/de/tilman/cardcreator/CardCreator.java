package de.tilman.cardcreator;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URL;
import java.util.Arrays;
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
	
	public static final float mmUnit = 72f/25.4f; // see https://itextpdf.com/en/resources/faq/technical-support/itext-7/how-define-width-cell
	public static final int cardsPerLine = 6;
	public static final float cardWidth = 40;
	public static final float pageWidth = 297;
	public static final float borderWidth = 1;

	public static final PdfNumber INVERTEDPORTRAIT = new PdfNumber(180);
	public static final PdfNumber LANDSCAPE = new PdfNumber(90);
	public static final PdfNumber PORTRAIT = new PdfNumber(0);
	public static final PdfNumber SEASCAPE = new PdfNumber(270);
	
	private Properties properties;
	
	
	private SpotifyApi spotifyApi;
	private ClientCredentialsRequest clientCredentialsRequest;
	private Image spotifyLogo;

	public CardCreator(List<String> links) {
		
		List<Card> cards;
		
		try {
			// get properties
			Reader reader = new FileReader("config.properties");
			properties = new Properties();
			properties.load(reader);
			
			spotifyApi = new SpotifyApi.Builder().setClientId(properties.getProperty("spotifyClientId")).setClientSecret(properties.getProperty("spotifyClientSecret")).build();
			clientCredentialsRequest = spotifyApi.clientCredentials().build();
			
			final ClientCredentials clientCredentials = clientCredentialsRequest.execute();

			// Set access token for further "spotifyApi" object usage
			String accessToken = clientCredentials.getAccessToken();
			log.info(accessToken);
			spotifyApi.setAccessToken(accessToken);
			
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
			if (line.size() < cardsPerLine) {
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
		pdfDoc.setDefaultPageSize(PageSize.A4.rotate());
		Document doc = new Document(pdfDoc);
		
		float docMargin = (pageWidth - (cardWidth * cardsPerLine)) / 2 * mmUnit;
		doc.setMargins(0, docMargin, 0,  docMargin - borderWidth);  // leave space for the first border
		
		UnitValue[] colWidths = new UnitValue[cardsPerLine];
		for (int i = 0; i < colWidths.length; i++)
			colWidths[i] = new UnitValue(UnitValue.PERCENT, 100/cardsPerLine);
		
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
					qrcode.setWidth(22 * mmUnit); // Cannot set margin to 0 on QR codes and they grow with the length of the content. This is an approximation.
					p.setPaddingTop(22 * mmUnit - qrcode.getWidth().getValue() / 2); // mid point of QR code is 22 mm from top border
					p.add(qrcode);
					cell.add(p);
					cell2.add(p);
					
					p = new Paragraph();
					p.setTextAlignment(TextAlignment.CENTER);
					p.setPaddingTop(5 * mmUnit);
					if (card.cover != null) {
						Image cover = card.cover;
						cover.setWidth(30 * mmUnit);
						p.add(cover);
					}
					else {
						p.setPaddingBottom(30 * mmUnit);
					}
					cell.add(p);
					cell2.add(p);
					
					p = new Paragraph();
					p.setTextAlignment(TextAlignment.CENTER);
					p.setPaddingTop(5 * mmUnit);
					p.setFontSize(8f);
					p.add(card.artist);
					cell.add(p);
					cell2.add(p);
					
					p = new Paragraph();
					p.setTextAlignment(TextAlignment.CENTER);
					p.setPaddingTop(3 * mmUnit);
					p.setFontSize(9f);
					p.setBold();
					p.add(card.title);
					p.setMaxHeight(22 * mmUnit);
					cell.add(p);
					cell2.add(p);
				}
				
				cell.setWidth((cardWidth * mmUnit) - borderWidth);
				table.addCell(cell.setRotationAngle(Math.PI));
				table2.addCell(cell2);
			}
			
			table.setMinHeight(105 * mmUnit);
			table.setMaxHeight(105 * mmUnit);
			doc.add(table);
			
			table2.setMinHeight(105 * mmUnit);
			table2.setMaxHeight(105 * mmUnit);
			doc.add(table2);
		}

		doc.close();
	}
	

	public static void main(String[] args) throws Exception {
		
		List<String> links = Arrays.asList(
				
				// A QR test card with long text and unicode characters
				"https://ih1.redbubble.net/image.859117621.1102/ra,unisex_tshirt,x2200,101010:01c5ca27c6,front-c,267,146,1000,1000-bg,f8f8f8.u6.jpg|German: ÄäÖöÜüß, Russian: ЯБГДЖЙ, Polish: ŁĄŻĘĆŃŚŹ, Japanese: Kanji: てすと   (te-su-to), Hankaku: ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃ",
				
				// Internet radio stream - the logo can be put as parameter, separated by a vertical line
				"https://upload.wikimedia.org/wikipedia/commons/thumb/4/4b/Radio_Teddy_Logo.svg/1200px-Radio_Teddy_Logo.svg.png|http://streamtdy.ir-media-tec.com/live/mp3-128/web/play.mp3",
				
				// A folder on a network share. The creator will look for a file named "cover.jpg".
				"NAS/share/Drachenreiter - Das Hörspiel",
				
				// Spotify
				"spotify:album:0YSWLbCg5SpxAb2VOON3mX", // Sing mit mir Kinderlieder
				"spotify:album:7tfjSYq5aWFwKlYWRzmPMc" // Das kleine Gespenst
				);
		
		new CardCreator(links);
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