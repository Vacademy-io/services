import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { WhatsAppTemplatePreview } from './WhatsAppTemplatePreview';

/**
 * The send dialogs used to preview a template as its `bodyText` in a grey box, so an admin approved
 * a send having never seen the header, the footer or the buttons that go out with it — and read
 * `Dear {{1}}` where the message says a name. This asserts the whole approved template renders.
 */
const template = {
    name: 'batch_portal_update',
    headerType: 'TEXT',
    headerText: 'Shiksha Nation',
    bodyText: 'Dear {{1}},\nYour {{2}} batch starts tomorrow.',
    footerText: 'Reply STOP to opt out',
    bodyVariableNames: ['name', 'course_name'],
    buttons: [{ type: 'URL', text: 'Open the app', url: 'https://example.com' }],
};

describe('WhatsAppTemplatePreview', () => {
    it('renders header, body, footer and buttons', () => {
        render(<WhatsAppTemplatePreview template={template} />);

        expect(screen.getByText('Shiksha Nation')).toBeInTheDocument();
        expect(screen.getByText(/Your/)).toBeInTheDocument();
        expect(screen.getByText('Reply STOP to opt out')).toBeInTheDocument();
        expect(screen.getByText('Open the app')).toBeInTheDocument();
    });

    it('labels each placeholder with its field until a value is known', () => {
        render(<WhatsAppTemplatePreview template={template} />);

        expect(screen.getByText('name')).toBeInTheDocument();
        expect(screen.getByText('course_name')).toBeInTheDocument();
        expect(screen.queryByText(/\{\{1\}\}/)).not.toBeInTheDocument();
    });

    it('substitutes resolved values', () => {
        render(
            <WhatsAppTemplatePreview
                template={template}
                values={{ name: 'Priya', course_name: 'NEET 2027' }}
            />
        );

        expect(screen.getByText('Priya')).toBeInTheDocument();
        expect(screen.getByText('NEET 2027')).toBeInTheDocument();
        expect(screen.queryByText('name')).not.toBeInTheDocument();
    });

    it('shows the media actually being sent, not the approved sample', () => {
        render(
            <WhatsAppTemplatePreview
                template={{ ...template, headerType: 'IMAGE', headerSampleUrl: 'https://a/s.png' }}
                mediaUrl="https://b/live.png"
                labels={{ image: 'Image' }}
            />
        );

        expect(screen.getByRole('img', { name: 'Image' })).toHaveAttribute(
            'src',
            'https://b/live.png'
        );
    });

    it('does not leak body values into a positional header', () => {
        // Header placeholders are numbered in their own namespace: `{{1}}` in the header is not the
        // body's `{{1}}`, and filling it from the body's values would preview a lie.
        render(
            <WhatsAppTemplatePreview
                template={{
                    headerType: 'TEXT',
                    headerText: 'Update for {{1}}',
                    headerSampleValues: ['Class 10'],
                    bodyText: 'Dear {{1}}',
                }}
                values={{ '1': 'Priya' }}
            />
        );

        expect(screen.getByText('Class 10')).toBeInTheDocument();
        expect(screen.getByText('Priya')).toBeInTheDocument();
    });

    it('says so when a template has no body', () => {
        render(
            <WhatsAppTemplatePreview
                template={{ bodyText: '' }}
                labels={{ emptyBody: 'No message body.' }}
            />
        );

        expect(screen.getByText('No message body.')).toBeInTheDocument();
    });
});
