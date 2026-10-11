use thiserror::Error;

#[derive(Error, Debug)]
pub enum ToolkitError {
    #[error("I/O error: {0}")]
    IoError(#[from] std::io::Error),

    #[error("File not found: {0}")]
    FileNotFound(String),

    #[error("Unsupported format: {0}")]
    UnsupportedFormat(String),

    #[error("Invalid or corrupted file: {0}")]
    CorruptedFile(String),

    #[error("Image processing error: {0}")]
    ImageError(String),

    #[error("PDF processing error: {0}")]
    PdfError(String),

    #[error("Invalid parameter: {0}")]
    InvalidParameter(String),

    #[error("Document is empty")]
    EmptyDocument,

    #[error("Operation was cancelled")]
    Cancelled,

    #[error("Serialization error: {0}")]
    SerializationError(#[from] serde_json::Error),
}

impl From<image::ImageError> for ToolkitError {
    fn from(err: image::ImageError) -> Self {
        ToolkitError::ImageError(err.to_string())
    }
}

impl From<lopdf::Error> for ToolkitError {
    fn from(err: lopdf::Error) -> Self {
        ToolkitError::PdfError(err.to_string())
    }
}
