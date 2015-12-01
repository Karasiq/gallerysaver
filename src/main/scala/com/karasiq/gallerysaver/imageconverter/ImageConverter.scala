package com.karasiq.gallerysaver.imageconverter

/**
  * Abstract image converter
  */
abstract class ImageConverter {
  def newExtension: String
  def convert(input: ImageSource, output: ImageDestination): Unit
}