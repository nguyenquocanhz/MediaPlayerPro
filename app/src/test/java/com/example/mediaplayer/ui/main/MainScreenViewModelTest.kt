package com.example.mediaplayer.ui.main

import kotlinx.coroutines.test.runTest
import org.junit.Test
import com.example.mediaplayer.data.NctScraper

class MainScreenViewModelTest {
  @Test
  fun scraperTest() = runTest {
    println("--- TESTING NCT SCRAPER OBJECT ---")
    val query = "nàng thơ"
    
    val songs = NctScraper.searchSong(query)
    println("Found ${songs.size} songs:")
    for (song in songs.take(5)) {
      println(" - Title: '${song.title}', Artist: '${song.artist}'")
      println("   Thumb: '${song.thumbnailUrl}'")
      println("   Page:  '${song.pageUrl}'")
    }

    if (songs.isNotEmpty()) {
      val firstSong = songs.first()
      println("\nGetting stream URL for: '${firstSong.title}'")
      val streamUrl = NctScraper.getStreamUrl(firstSong.pageUrl)
      println("Extracted Stream URL: $streamUrl")
      
      assert(streamUrl.isNotEmpty()) { "Stream URL must not be empty" }
      assert(streamUrl.startsWith("http")) { "Stream URL must start with http" }
    } else {
      assert(false) { "No songs returned from search" }
    }
  }
}

